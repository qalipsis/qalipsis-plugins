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

package io.qalipsis.plugins.r2dbc.jasync.save

import com.github.jasync.sql.db.SuspendingConnection
import com.github.jasync.sql.db.mysql.MySQLQueryResult
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.context.StepError
import io.qalipsis.api.context.StepName
import io.qalipsis.api.context.StepStartStopContext
import io.qalipsis.api.events.EventsLogger
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.meters.CampaignMeterRegistry
import io.qalipsis.api.meters.Counter
import io.qalipsis.api.meters.Timer
import io.qalipsis.api.report.ReportMessageSeverity
import io.qalipsis.api.retry.RetryPolicy
import io.qalipsis.api.steps.AbstractStep
import io.qalipsis.plugins.r2dbc.jasync.dialect.Dialect
import java.time.Duration
import java.util.concurrent.TimeUnit

/**
 * Implementation of a [io.qalipsis.api.steps.Step] able to perform a save on the database.
 *
 * @property connectionPoolBuilder closure to generate connection to database.
 * @property tableNameFactory name of the database.
 * @property columnsFactory name of the columns on the database.
 * @property recordsFactory list of rows to add to database.
 * @property meterRegistry metrics to track.
 *
 * @author Carlos Vieira
 */
internal class JasyncSaveStep<I>(
    id: StepName,
    retryPolicy: RetryPolicy?,
    private val connectionPoolBuilder: () -> SuspendingConnection,
    private val dialect: Dialect,
    private val tableNameFactory: suspend (ctx: StepContext<*, *>, input: I) -> String,
    private val columnsFactory: suspend (ctx: StepContext<*, *>, input: I) -> List<String>,
    private val recordsFactory: suspend (ctx: StepContext<*, *>, input: I) -> List<JasyncSaveRecord>,
    private val meterRegistry: CampaignMeterRegistry?,
    private val eventsLogger: EventsLogger?
) : AbstractStep<I, JasyncSaveResult<I>>(id, retryPolicy) {

    private lateinit var connection: SuspendingConnection

    private val eventPrefix: String = "r2dbc.jasync.save"

    private val meterPrefix: String = "r2dbc-jasync-save"

    private var recordsCounter: Counter? = null

    private var successCounter: Counter? = null

    private var timeToResponse: Timer? = null

    private var failureCounter: Counter? = null

    override suspend fun start(context: StepStartStopContext) {
        meterRegistry?.apply {
            val tags = context.toMetersTags()
            val scenarioName = context.scenarioName
            val stepName = context.stepName
            recordsCounter = counter(scenarioName, stepName, "$meterPrefix-records", tags).report {
                display(
                    format = "attempted saves: %,.0f",
                    severity = ReportMessageSeverity.INFO,
                    row = 0,
                    column = 0,
                    Counter::count
                )
            }
            failureCounter = counter(scenarioName, stepName, "$meterPrefix-records-failures", tags).report {
                display(
                    format = "\u2716 %,.0f failures",
                    severity = ReportMessageSeverity.ERROR,
                    row = 0,
                    column = 4,
                    Counter::count
                )
            }
            successCounter = counter(scenarioName, stepName, "$meterPrefix-records-success", tags).report {
                display(
                    format = "\u2713 %,.0f successes",
                    severity = ReportMessageSeverity.INFO,
                    row = 0,
                    column = 2,
                    Counter::count
                )
            }
            timeToResponse = timer(scenarioName, stepName, "$meterPrefix-records-time-to-response", tags)

        }
        connection = connectionPoolBuilder()
        connection.connect()
    }

    override suspend fun stop(context: StepStartStopContext) {
        meterRegistry?.apply {
            recordsCounter = null
            failureCounter = null
            successCounter = null
            timeToResponse = null
        }
        connection.disconnect()
    }

    override suspend fun execute(context: StepContext<I, JasyncSaveResult<I>>) {
        val input = context.receive()
        val tableName = tableNameFactory(context, input)
        val columns = columnsFactory(context, input)
        val records = recordsFactory(context, input)
        val ids = mutableListOf<Long>()

        var successSavedDocuments = 0
        var failedSavedDocuments = 0
        eventsLogger?.info("${eventPrefix}.records", records.size, tags = context.toEventTags())
        recordsCounter?.increment(records.size.toDouble())
        val requestStart = System.nanoTime()
        records.forEach {
            try {
                val result =
                    connection.sendPreparedStatement(buildQuery(dialect, tableName, columns), it.parameters, true)
                // Only the implementation MySQLQueryResult provides the last insert ID.
                (result as? MySQLQueryResult)?.let {
                    ids.add(it.lastInsertId)
                }
                successSavedDocuments++
            } catch (e: Exception) {
                log.debug(e) { "${e.message}" }
                failedSavedDocuments++
                context.addError(StepError(e))
            }
        }
        val timeToResponse = Duration.ofNanos(System.nanoTime() - requestStart)
        eventsLogger?.info("${eventPrefix}.records.time-to-response", timeToResponse, tags = context.toEventTags())
        this.timeToResponse?.record(timeToResponse.toNanos(), TimeUnit.NANOSECONDS)
        if (successSavedDocuments > 0) {
            successCounter?.increment(successSavedDocuments.toDouble())
            eventsLogger?.info("${eventPrefix}.records.success", successSavedDocuments, tags = context.toEventTags())
        }
        if (failedSavedDocuments > 0) {
            eventsLogger?.info("${eventPrefix}.records.failure", failedSavedDocuments, tags = context.toEventTags())
            failureCounter?.increment(failedSavedDocuments.toDouble())
        }

        context.send(
            JasyncSaveResult(
                input,
                ids,
                JasyncQueryMeters(
                    totalDocuments = records.size,
                    timeToResponse = timeToResponse,
                    successSavedDocuments = successSavedDocuments
                )
            )
        )
    }

    /**
     * Creates the prepared statements to insert a record into the table.
     */
    private fun buildQuery(dialect: Dialect, tableName: String, columns: List<String>): String {
        val quotedTableName = dialect.quote(tableName)
        val quotedColumns = columns.joinToString { dialect.quote(it) }
        val arguments = columns.joinToString { "?" }
        return "INSERT INTO $quotedTableName ($quotedColumns) VALUES ($arguments)"
    }

    private companion object {
        val log = logger()
    }
}
