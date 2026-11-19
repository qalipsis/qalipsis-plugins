/*
 * QALIPSIS
 * Copyright (C) 2025 AERIS IT Solutions GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package io.qalipsis.plugins.sql.search

import io.qalipsis.api.context.StepContext
import io.qalipsis.api.context.StepError
import io.qalipsis.api.context.StepName
import io.qalipsis.api.context.StepOutput
import io.qalipsis.api.context.StepStartStopContext
import io.qalipsis.api.retry.RetryPolicy
import io.qalipsis.api.steps.AbstractStep
import io.qalipsis.api.steps.datasource.DatasourceException
import io.qalipsis.plugins.sql.converters.SqlResultSetConverter
import io.qalipsis.plugins.sql.dialect.Dialect
import io.qalipsis.plugins.sql.r2dbc.acquireConnection
import io.qalipsis.plugins.sql.r2dbc.closeConnection
import io.qalipsis.plugins.sql.r2dbc.executePreparedQuery
import io.r2dbc.pool.ConnectionPool
import java.time.Duration
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.reactive.awaitFirstOrNull

/**
 * Implementation of a [io.qalipsis.api.steps.Step] able to perform any kind of query to get records from database.
 *
 * @property connectionPoolFactory closure to generate connection pool to database
 * @property dialect the database dialect to use
 * @property queryFactory closure to generate the string for the query
 * @property parametersFactory closure to generate a list for the query parameters
 * @property converter converts and returns results to the output channel
 *
 * @author Fiodar Hmyza
 */
internal class SqlSearchStep<I>(
    id: StepName,
    retryPolicy: RetryPolicy?,
    private val connectionPoolFactory: () -> ConnectionPool,
    private val dialect: Dialect,
    private val queryFactory: (suspend (ctx: StepContext<*, *>, input: I) -> String),
    private val parametersFactory: (suspend (ctx: StepContext<*, *>, input: I) -> List<*>),
    private val converter: SqlResultSetConverter<ResultSetWrapper, Any?, I>
) : AbstractStep<I, SqlSearchBatchResults<I, Map<String, Any?>>>(id, retryPolicy) {

    private lateinit var connectionPool: ConnectionPool

    override suspend fun start(context: StepStartStopContext) {
        connectionPool = connectionPoolFactory()
    }

    override suspend fun stop(context: StepStartStopContext) {
        connectionPool.disposeLater().awaitFirstOrNull()
    }

    override suspend fun execute(context: StepContext<I, SqlSearchBatchResults<I, Map<String, Any?>>>) {

        val input = context.receive()

        val query = dialect.convertPlaceholders(queryFactory(context, input))
        val parameters = parametersFactory(context, input)
        val requestStart = System.nanoTime()
        val connection = connectionPool.acquireConnection()
        try {
            val resultSet = connection.executePreparedQuery(query, parameters)
            val timeToResponse = Duration.ofNanos(System.nanoTime() - requestStart)
            val result = ResultSetWrapper(
                resultSet = resultSet,
                timeToResponse = timeToResponse
            )
            val rowIndex = AtomicLong()

            try {
                @Suppress("UNCHECKED_CAST")
                converter.supply(rowIndex, result, input, context as StepOutput<Any?>, context.toEventTags())
            } catch (e: Exception) {
                context.addError(StepError(DatasourceException(rowIndex.get() - 1, e.message)))
            }
        } finally {
            connection.closeConnection()
        }
    }
}
