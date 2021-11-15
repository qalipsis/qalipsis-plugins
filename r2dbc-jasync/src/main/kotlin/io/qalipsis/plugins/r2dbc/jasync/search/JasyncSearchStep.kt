package io.qalipsis.plugins.r2dbc.jasync.search

import com.github.jasync.sql.db.ResultSet
import com.github.jasync.sql.db.SuspendingConnection
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.context.StepError
import io.qalipsis.api.context.StepId
import io.qalipsis.api.context.StepOutput
import io.qalipsis.api.context.StepStartStopContext
import io.qalipsis.api.retry.RetryPolicy
import io.qalipsis.api.steps.AbstractStep
import io.qalipsis.api.steps.datasource.DatasourceException
import io.qalipsis.api.steps.datasource.DatasourceRecord
import io.qalipsis.plugins.r2dbc.jasync.converters.JasyncResultSetConverter
import java.util.concurrent.atomic.AtomicLong

/**
 * Implementation of a [io.qalipsis.api.steps.Step] able to perform any kind of query to get records from database.
 *
 * @property connectionsPoolFactory closure to generate connection to database
 * @property queryFactory closure to generate the string for the query
 * @property parametersFactory closure to generate a list for the query parameters
 * @property converter converts and returns results to the output channel
 *
 * @author Fiodar Hmyza
 */
internal class JasyncSearchStep<I>(
    id: StepId,
    retryPolicy: RetryPolicy?,
    private val connectionsPoolFactory: () -> SuspendingConnection,
    private val queryFactory: (suspend (ctx: StepContext<*, *>, input: I) -> String),
    private val parametersFactory: (suspend (ctx: StepContext<*, *>, input: I) -> List<*>),
    private val converter: JasyncResultSetConverter<ResultSet, Any?, I>
) : AbstractStep<I, Pair<I, List<DatasourceRecord<Map<String, Any?>>>>>(id, retryPolicy) {

    private lateinit var connection: SuspendingConnection

    override suspend fun start(context: StepStartStopContext) {
        connection = connectionsPoolFactory()
        connection.connect()
    }

    override suspend fun stop(context: StepStartStopContext) {
        connection.disconnect()
    }

    override suspend fun execute(context: StepContext<I, Pair<I, List<DatasourceRecord<Map<String, Any?>>>>>) {
        val input = context.receive()

        val query = queryFactory(context, input)
        val parameters = parametersFactory(context, input)

        val result = connection.sendPreparedStatement(query, parameters, true).rows
        val rowIndex = AtomicLong()

        try {
            @Suppress("UNCHECKED_CAST")
            converter.supply(rowIndex, result, input, context as StepOutput<Any?>)
        } catch (e: Exception) {
            context.addError(StepError(DatasourceException(rowIndex.get() - 1, e.message)))
        }
    }
}
