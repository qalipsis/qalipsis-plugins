package io.qalipsis.plugins.graphite.save

import io.qalipsis.api.context.StepContext
import io.qalipsis.api.context.StepId
import io.qalipsis.api.context.StepStartStopContext
import io.qalipsis.api.retry.RetryPolicy
import io.qalipsis.api.steps.AbstractStep

/**
 * Implementation of a [io.qalipsis.api.steps.Step] able to perform inserts into Graphite.
 *
 * @property GraphiteSaveMessageClient client to use to execute the io.qalipsis.plugins.graphite.save for the current step.
 * @property messageFactory closure to generate a list of messages.
 *
 * @author Palina Bril
 */
internal class GraphiteSaveStep<I>(
    id: StepId,
    retryPolicy: RetryPolicy?,
    private val graphiteSaveMessageClient: GraphiteSaveMessageClient,
    private val messageFactory: (suspend (ctx: StepContext<*, *>, input: I) -> List<String>)
) : AbstractStep<I, GraphiteSaveResult<I>>(id, retryPolicy) {

    override suspend fun start(context: StepStartStopContext) {
        graphiteSaveMessageClient.start(context)
    }

    override suspend fun execute(context: StepContext<I, GraphiteSaveResult<I>>) {
        val input = context.receive()
        val messages = messageFactory(context, input)

        val metrics = graphiteSaveMessageClient.execute(messages, context.toEventTags())

        context.send(GraphiteSaveResult(input, messages, metrics))
    }

    override suspend fun stop(context: StepStartStopContext) {
        graphiteSaveMessageClient.stop(context)
    }
}
