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

import com.fasterxml.jackson.databind.ObjectMapper
import io.micronaut.context.annotation.Requires
import io.qalipsis.api.Executors
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.query.AggregationQueryExecutionContext
import io.qalipsis.api.query.DataRetrievalQueryExecutionContext
import io.qalipsis.api.query.Page
import io.qalipsis.api.report.TimeSeriesDataProvider
import io.qalipsis.api.report.TimeSeriesMeter
import io.qalipsis.api.report.TimeSeriesRecord
import io.qalipsis.api.report.TimeSeriesValues
import io.qalipsis.plugins.timescaledb.event.TimescaledbEventDataProviderConfiguration
import io.qalipsis.plugins.timescaledb.meter.TimescaledbMeterDataProviderConfiguration
import io.qalipsis.plugins.timescaledb.utils.DbUtils
import io.r2dbc.pool.ConnectionPool
import io.r2dbc.spi.Connection
import jakarta.annotation.Nullable
import jakarta.inject.Named
import jakarta.inject.Singleton
import java.time.Duration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.reactor.awaitSingleOrNull
import kotlinx.coroutines.withTimeout
import reactor.core.publisher.Flux

@Singleton
@Requires(bean = AbstractDataProvider::class)
internal class TimescaledbTimeSeriesDataProvider(
    private val objectMapper: ObjectMapper,
    @Nullable @Named("event-data-provider") private val eventConnectionPool: ConnectionPool?,
    @Nullable @Named("meter-data-provider") private val meterConnectionPool: ConnectionPool?,
    @Named(Executors.IO_EXECUTOR_NAME) private val ioCoroutineScope: CoroutineScope,
    private val timeSeriesMeterRecordConverter: TimeSeriesMeterRecordConverter,
    private val timeSeriesEventRecordConverter: TimeSeriesEventRecordConverter,
    @Nullable private val timescaledbEventDataProviderConfiguration: TimescaledbEventDataProviderConfiguration?,
    @Nullable private val timescaledbMeterDataProviderConfiguration: TimescaledbMeterDataProviderConfiguration?
) : TimeSeriesDataProvider {

    override suspend fun executeAggregations(
        preparedQueries: Map<String, String>,
        context: AggregationQueryExecutionContext,
    ): Map<String, TimeSeriesValues> {
        return prepareAndExecuteConcurrentQueries(preparedQueries) { connectionPool, databaseSchema, query ->
            buildAggregationExecutor(
                connectionPool,
                databaseSchema,
                context,
                query
            )
        }
    }

    private fun buildAggregationExecutor(
        connectionPool: ConnectionPool,
        databaseSchema: String,
        context: AggregationQueryExecutionContext,
        query: PreparedQueries
    ) = AggregationExecutor(
        connectionPool = connectionPool,
        databaseSchema = databaseSchema,
        context = context,
        statement = query.aggregationStatement,
        boundParameters = query.aggregationBoundParameters,
        nextParameterIndex = query.nextAvailableAggregationParameterIdentifierIndex,
        dataType = query.dataType,
        splitByScope = query.dataType == DataType.METER
    )

    override suspend fun retrieveRecords(
        preparedQueries: Map<String, String>,
        context: DataRetrievalQueryExecutionContext
    ): Map<String, Page<TimeSeriesRecord>> {
        return prepareAndExecuteConcurrentQueries(preparedQueries) { connectionPool, databaseSchema, query ->
            buildRetrievalExecutor(
                connectionPool,
                databaseSchema,
                context,
                query
            )
        }
    }

    override suspend fun retrieveUsedStorage(tenant: String): Long {
        val eventsStorage = eventConnectionPool?.let { connection ->
            DbUtils.fetchStorage(
                connection,
                tenant,
                timescaledbEventDataProviderConfiguration?.schema!!,
                "events"
            )
        } ?: 0
        val metersStorage = meterConnectionPool?.let { connection ->
            DbUtils.fetchStorage(
                connection,
                tenant,
                timescaledbMeterDataProviderConfiguration?.schema!!,
                "meters"
            )
        } ?: 0

        return eventsStorage + metersStorage
    }

    override suspend fun retrieveCampaignMeters(
        tenant: String,
        campaignKeys: Collection<String>,
        scenarioNames: Collection<String>,
    ): List<TimeSeriesMeter> {
        val connectionPool = meterConnectionPool ?: return emptyList()
        val schema = timescaledbMeterDataProviderConfiguration?.schema ?: return emptyList()

        val params = mutableMapOf<String, Any>()
        params["\$1"] = tenant
        params["\$2"] = campaignKeys.toTypedArray()

        val sql = StringBuilder(
            """SELECT name, timestamp, type, campaign, scenario, tags, count, sum, mean, max, value, other"""
                    + """ FROM $schema.meters WHERE tenant = $1 AND campaign = any(array[$2]) AND tags->>'scope' = 'campaign'"""
        )

        if (scenarioNames.isNotEmpty()) {
            params["\$3"] = scenarioNames.toTypedArray()
            sql.append(""" AND scenario = any (array[$3])""")
        }

        sql.append(""" ORDER BY name, timestamp""")

        val query = sql.toString()
        log.debug { "Retrieving campaign meters with query: $query" }

        return Flux.usingWhen(
            connectionPool.create()
                .doOnNext { log.debug { "Acquired a connection" } },
            { connection ->
                Flux.from(
                    connection.createStatement(query)
                        .also {
                            params.forEach { (binding, value) ->
                                log.trace { "Binding $binding to $value" }
                                it.bind(binding, value)
                            }
                        }.execute()
                ).flatMap { result ->
                    result.map { row, metadata ->
                        timeSeriesMeterRecordConverter.convert(row, metadata)
                    }
                }
            },
            Connection::close
        ).collectList().awaitSingleOrNull().orEmpty()
            .filterIsInstance<TimeSeriesMeter>()
    }

    private fun buildRetrievalExecutor(
        connectionPool: ConnectionPool,
        databaseSchema: String,
        context: DataRetrievalQueryExecutionContext,
        query: PreparedQueries
    ) = DataRetrievalExecutor(
        ioCoroutineScope = ioCoroutineScope,
        converter = when (query.dataType) {
            DataType.METER -> timeSeriesMeterRecordConverter
            DataType.EVENT -> timeSeriesEventRecordConverter
        },
        connectionPool = connectionPool,
        databaseSchema = databaseSchema,
        context = context,
        countStatement = query.countStatement,
        selectStatement = query.retrievalStatement,
        boundParameters = query.retrievalBoundParameters,
        nextParameterIndex = query.nextAvailableRetrievalParameterIdentifierIndex,
        dataType = query.dataType
    )

    private suspend fun <T> prepareAndExecuteConcurrentQueries(
        preparedQueries: Map<String, String>,
        executorBuilder: (connectionPool: ConnectionPool, databaseSchema: String, query: PreparedQueries) -> AbstractQueryExecutor<T>
    ): Map<String, T> {
        val keyedJobs = preparedQueries
            .mapValues {
                val query = objectMapper.readValue(it.value, PreparedQueries::class.java)
                val (connectionPool, databaseSchema) = when (query.dataType) {
                    DataType.METER -> requireNotNull(meterConnectionPool) { "The TimescaleDB meters provider was not enabled" } to requireNotNull(
                        timescaledbMeterDataProviderConfiguration?.schema
                    ) { "The TimescaleDB meters provider was not enabled" }

                    DataType.EVENT -> requireNotNull(eventConnectionPool) { "The TimescaleDB events provider was not enabled" } to requireNotNull(
                        timescaledbEventDataProviderConfiguration?.schema
                    ) { "The TimescaleDB events provider was not enabled" }
                }
                val executor = executorBuilder(connectionPool, databaseSchema, query)
                ioCoroutineScope.async {
                    withTimeout(Duration.ofSeconds(20).toMillis()) {
                        executor.execute()
                    }
                }
            }.toList()

        return flowOf(*keyedJobs.toTypedArray())
            .mapNotNull { (key, job) ->
                try {
                    key to job.await()
                } catch (e: Exception) {
                    log.error(e) { "" }
                    null
                }
            }.toList(mutableListOf())
            .toMap()
    }

    private companion object {

        val log = logger()
    }
}