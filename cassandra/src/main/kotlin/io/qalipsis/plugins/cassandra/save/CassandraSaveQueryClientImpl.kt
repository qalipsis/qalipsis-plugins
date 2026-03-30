/*
 * QALIPSIS
 * Copyright (C) 2025 AERIS IT Solutions GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package io.qalipsis.plugins.cassandra.save

import com.datastax.oss.driver.api.core.CqlSession
import com.datastax.oss.driver.api.core.cql.BatchStatement
import com.datastax.oss.driver.api.core.cql.BoundStatement
import com.datastax.oss.driver.api.core.cql.DefaultBatchType
import com.datastax.oss.driver.api.core.cql.Statement
import io.qalipsis.api.context.StepStartStopContext
import io.qalipsis.api.events.EventsLogger
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.meters.CampaignMeterRegistry
import io.qalipsis.api.meters.Counter
import io.qalipsis.api.meters.Timer
import io.qalipsis.api.report.ReportMessageSeverity
import io.qalipsis.api.sync.asSuspended
import io.qalipsis.plugins.cassandra.save.CassandraSaveQueryClientImpl.Companion.MAX_BATCH_SIZE
import java.time.Duration


/**
 * Implementation of [CassandraSaveQueryClient].
 * Client to save records in Cassandra.
 *
 * @property meterRegistry the metrics for the query operation.
 * @property eventsLogger the logger for events to track what happens during save query execution.
 *
 * @author Svetlana Paliashchuk
 */
internal class CassandraSaveQueryClientImpl(
    private val eventsLogger: EventsLogger?,
    private val meterRegistry: CampaignMeterRegistry?
) : CassandraSaveQueryClient {

    private val eventPrefix = "cassandra.save"

    private val meterPrefix = "cassandra-save"

    private var recordsToBeSent: Counter? = null

    private var timeToSuccess: Timer? = null

    private var timeToFailure: Timer? = null

    private var savedDocuments: Counter? = null

    private var failedDocuments: Counter? = null

    override suspend fun start(context: StepStartStopContext) {
        meterRegistry?.apply {
            val tags = context.toMetersTags()
            val scenarioName = context.scenarioName
            val stepName = context.stepName
            recordsToBeSent = counter(scenarioName, stepName, "$meterPrefix-saving-documents", tags).report {
                display(
                    format = "attempted save %,.0f",
                    severity = ReportMessageSeverity.INFO,
                    row = 0,
                    column = 0,
                    Counter::count
                )
            }
            timeToSuccess = timer(scenarioName, stepName, "$meterPrefix-time-to-response", tags)
            timeToFailure = timer(scenarioName, stepName, "$meterPrefix-time-to-failure", tags)
            savedDocuments = counter(scenarioName, stepName, "$meterPrefix-saved-documents", tags).report {
                display(
                    format = "\u2716 %,.0f successes",
                    severity = ReportMessageSeverity.INFO,
                    row = 0,
                    column = 1,
                    Counter::count
                )
            }
            failedDocuments = counter(scenarioName, stepName, "$meterPrefix-failed-documents", tags).report {
                display(
                    format = "\u2716 %,.0f failures",
                    severity = ReportMessageSeverity.ERROR,
                    row = 0,
                    column = 2,
                    Counter::count
                )
            }
        }
    }

    override suspend fun stop(context: StepStartStopContext) {
        meterRegistry?.apply {
            recordsToBeSent = null
            timeToSuccess = null
            timeToFailure = null
            savedDocuments = null
            failedDocuments = null
        }
    }

    /**
     * Executes save query.
     */
    override suspend fun execute(
        session: CqlSession,
        tableName: String,
        columns: List<String>,
        rows: List<CassandraSaveRow>,
        contextEventTags: Map<String, String>
    ): CassandraSaveQueryMeters {
        var failedDocumentsCount = 0
        var savedDocumentsCount = 0
        eventsLogger?.debug("$eventPrefix.saving-documents", rows.size, tags = contextEventTags)
        recordsToBeSent?.increment(rows.size.toDouble())

        val placeholders = columns.joinToString { "?" }
        val cql = "INSERT INTO $tableName (${columns.joinToString()}) VALUES ($placeholders)"
        val preparedStatement = session.prepare(cql)

        val boundStatements = mutableListOf<BoundStatement>()
        rows.forEach { row ->
            if (columns.size == row.args.size) {
                boundStatements += preparedStatement.bind(*row.args.toTypedArray())
            } else {
                log.warn { "Skipping row with ${row.args.size} arguments (expected ${columns.size} columns)" }
                failedDocumentsCount++
            }
        }

        val executables = buildExecutableStatements(boundStatements)

        val requestStart = System.nanoTime()
        val timeToResponse = try {
            val futures = executables.map { (statement, count) ->
                session.executeAsync(statement).asSuspended() to count
            }
            futures.forEach { (future, count) ->
                try {
                    future.get()
                    savedDocumentsCount += count
                } catch (e: Exception) {
                    log.warn(e) { "Failed to execute save statement" }
                    failedDocumentsCount += count
                }
            }
            Duration.ofNanos(System.nanoTime() - requestStart)
        } catch (e: Exception) {
            val timeToResponse = Duration.ofNanos(System.nanoTime() - requestStart)
            eventsLogger?.warn("$eventPrefix.failure", arrayOf(timeToResponse, e), tags = contextEventTags)
            timeToFailure?.record(timeToResponse)
            throw e
        }
        require(savedDocumentsCount > 0) { "None of the rows could be saved" }

        eventsLogger?.info(
            "$eventPrefix.saved-documents",
            arrayOf(savedDocumentsCount, timeToResponse),
            tags = contextEventTags
        )
        savedDocuments?.increment(savedDocumentsCount.toDouble())
        if (failedDocumentsCount > 0) {
            eventsLogger?.warn("$eventPrefix.failed-documents", failedDocumentsCount, tags = contextEventTags)
            failedDocuments?.increment(failedDocumentsCount.toDouble())
        }

        timeToSuccess?.record(timeToResponse)

        return CassandraSaveQueryMeters(
            rows.size, timeToResponse, savedDocumentsCount, failedDocumentsCount
        )
    }

    /**
     * Groups bound statements by partition (routing token) and builds executable statements.
     * Same-partition statements are grouped into UNLOGGED batches (chunked at [MAX_BATCH_SIZE]).
     * Statements with no routing token or alone in their partition are executed individually.
     */
    private fun buildExecutableStatements(
        boundStatements: List<BoundStatement>,
    ): List<Pair<Statement<*>, Int>> {
        val grouped = boundStatements.groupBy { it.routingToken }
        val executables = mutableListOf<Pair<Statement<*>, Int>>()

        grouped.forEach { (token, statements) ->
            if (token == null || statements.size == 1) {
                statements.forEach { executables += it to 1 }
            } else {
                statements.chunked(MAX_BATCH_SIZE).forEach { chunk ->
                    if (chunk.size == 1) {
                        executables += chunk[0] to 1
                    } else {
                        executables += BatchStatement.newInstance(
                            DefaultBatchType.UNLOGGED,
                            chunk
                        ) to chunk.size
                    }
                }
            }
        }

        return executables
    }

    companion object {
        @JvmStatic
        private val log = logger()

        private const val MAX_BATCH_SIZE = 25
    }
}
