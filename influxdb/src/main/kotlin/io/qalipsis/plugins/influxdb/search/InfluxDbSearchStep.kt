package io.qalipsis.plugins.influxdb.search

import com.influxdb.query.FluxRecord
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.context.StepId
import io.qalipsis.api.context.StepOutput
import io.qalipsis.api.context.StepStartStopContext
import io.qalipsis.api.retry.RetryPolicy
import io.qalipsis.api.steps.AbstractStep


/**
 * Implementation of a [io.qalipsis.api.steps.Step] able to perform any kind of query to get records from InfluxDB.
 *
 * @property influxDbQueryClient client to use to execute the io.qalipsis.plugins.influxdb.search for the current step
 * @property queryFactory closure to generate the filter for the query
 *
 * @author Palina Bril
 */
internal class InfluxDbSearchStep<I>(
    id: StepId,
    retryPolicy: RetryPolicy?,
    private val influxDbQueryClient: InfluxDbQueryClient,
    private val queryFactory: (suspend (ctx: StepContext<*, *>, input: I) -> String),
) : AbstractStep<I, Pair<I, List<FluxRecord>>>(id, retryPolicy) {

    override suspend fun start(context: StepStartStopContext) {
        influxDbQueryClient.start(context)
    }

    override suspend fun execute(context: StepContext<I, Pair<I, List<FluxRecord>>>) {
        val input = context.receive()
        val query = queryFactory(context, input)
        val results = influxDbQueryClient.execute(query, context.toEventTags())
        (context as StepOutput<Any?>).send(results)
    }

    override suspend fun stop(context: StepStartStopContext) {
        influxDbQueryClient.stop(context)
    }
}
