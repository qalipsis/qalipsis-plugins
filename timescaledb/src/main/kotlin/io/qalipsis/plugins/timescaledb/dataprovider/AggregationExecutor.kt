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

package io.qalipsis.plugins.timescaledb.dataprovider

import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.query.AggregationQueryExecutionContext
import io.qalipsis.api.report.TimeSeriesAggregationResult
import io.qalipsis.api.report.TimeSeriesValues
import io.r2dbc.pool.ConnectionPool
import io.r2dbc.spi.Connection
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.reactive.asFlow
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

/**
 * Specific class to execute a single aggregation on time-series data.
 *
 * @property connectionPool the pool of connection to the DB to use
 * @property context the context of the current execution
 * @property statement the SQL statement to complete for execution
 * @property boundParameters the parameters to bind to the string placeholders and prepared statement
 * @property nextParameterIndex next available index to use to add dynamic clauses to the request
 * @property splitByScope when true, rows with scope='campaign' are routed to [TimeSeriesValues.summary]
 *   and all other rows to [TimeSeriesValues.values]; requires a 'scope' column in the SELECT
 *
 * @author Eric Jessé
 */
internal class AggregationExecutor(
    private val connectionPool: ConnectionPool,
    private val databaseSchema: String,
    private val context: AggregationQueryExecutionContext,
    private val statement: String,
    private val boundParameters: Map<String, BoundParameter>,
    private val nextParameterIndex: Int,
    private val dataType: DataType? = null,
    private val splitByScope: Boolean = false,
) : AbstractQueryExecutor<TimeSeriesValues>() {

    override suspend fun execute(): TimeSeriesValues {
        val actualTimeframe = context.aggregationTimeframe.toMillis()
        val (actualStart, actualEnd) = roundStartAndEnd(actualTimeframe, context.from, context.until)

        val actualBoundParameters = boundParameters.toMutableMap()
        val additionalClauses = buildAdditionalClauses(
            campaignsReferences = context.campaignsReferences,
            scenariosNames = context.scenariosNames,
            zones = context.zones,
            actualBoundParameters = actualBoundParameters,
            nextParameterIndex = nextParameterIndex,
            dataType = dataType
        )

        val sqlStatement =
            String.format(
                statement
                    .replace("%timeframe%", "$actualTimeframe")
                    .replace("%schema%", databaseSchema),
                additionalClauses.toString()
            )
        log.trace { "Executing the prepared statement\n\t$sqlStatement \n\twith the bound parameters\n\t$actualBoundParameters" }
        val scopedResults = Flux.usingWhen(
            connectionPool.create(),
            { connection ->
                log.debug { "Executing the SQL query: $sqlStatement" }
                Mono.from(connection.createStatement(sqlStatement).also { statement ->
                    bindArguments(
                        tenant = context.tenant,
                        statement = statement,
                        boundParameter = actualBoundParameters,
                        actualStart = actualStart,
                        actualEnd = actualEnd
                    )
                }.execute())
                    .flatMapMany { result ->
                        log.trace { "Received a result to the query" }
                        val firstBucketsByKey = mutableMapOf<String, Instant>()
                        result.map { row, _ ->
                            val timestamp = (row["bucket"] as OffsetDateTime).toInstant()
                            val campaignKey = row["campaign"] as? String
                            val scope = if (splitByScope) row["scope"] as? String else null
                            val value = (row["result"] as Number?)?.let {
                                when (it) {
                                    is BigDecimal -> it
                                    is Long -> BigDecimal.valueOf(it)
                                    is Double -> BigDecimal.valueOf(it)
                                    else -> null
                                }
                            }

                            val bucketKey = "${campaignKey}:${scope}"
                            val elapsed = firstBucketsByKey.computeIfAbsent(bucketKey) { timestamp }
                                .let { Duration.between(it, timestamp) }
                            scope to TimeSeriesAggregationResult(
                                start = timestamp,
                                elapsed = elapsed,
                                campaign = campaignKey,
                                value = value
                            ).also {
                                log.trace { "Received the aggregation $it" }
                            }
                        }
                    }
            },
            Connection::close
        ).asFlow().toList(mutableListOf<Pair<String?, TimeSeriesAggregationResult>>())

        return if (splitByScope) {
            TimeSeriesValues(
                values = scopedResults.filter { it.first != "campaign" }.map { it.second },
                summary = scopedResults.firstOrNull { it.first == "campaign" }?.second
            )
        } else {
            TimeSeriesValues(
                values = scopedResults.map { it.second },
                summary = null
            )
        }
    }

    private companion object {
        val log = logger()
    }
}
