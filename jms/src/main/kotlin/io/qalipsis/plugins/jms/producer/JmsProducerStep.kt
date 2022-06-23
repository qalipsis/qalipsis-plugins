package io.qalipsis.plugins.jms.producer

import io.qalipsis.api.context.StepContext
import io.qalipsis.api.context.StepName
import io.qalipsis.api.context.StepStartStopContext
import io.qalipsis.api.retry.RetryPolicy
import io.qalipsis.api.steps.AbstractStep


/**
 * Implementation of a [io.qalipsis.api.steps.Step] able to produce native JMS [Message]s to a JMS server.
 *
 * @property jmsProducer producer to use to execute the producing for the current step
 * @property recordFactory closure to generate list of [JmsProducerRecord]
 *
 * @author Alexander Sosnovsky
 */
internal class JmsProducerStep<I>(
    stepId: StepName,
    retryPolicy: RetryPolicy?,
    private val jmsProducer: JmsProducer,
    private val recordFactory: (suspend (ctx: StepContext<*, *>, input: I) -> List<JmsProducerRecord>),
) : AbstractStep<I, JmsProducerResult<I>>(stepId, retryPolicy) {

    override suspend fun start(context: StepStartStopContext) {
        jmsProducer.start(context.toMetersTags())
    }

    override suspend fun execute(context: StepContext<I, JmsProducerResult<I>>) {
        val input = context.receive()
        val messages = recordFactory(context, input)

        val jmsProducerMeters = jmsProducer.execute(messages, context.toEventTags())

        context.send(JmsProducerResult(input, jmsProducerMeters))
    }

    override suspend fun stop(context: StepStartStopContext) {
        jmsProducer.stop()
    }

}
