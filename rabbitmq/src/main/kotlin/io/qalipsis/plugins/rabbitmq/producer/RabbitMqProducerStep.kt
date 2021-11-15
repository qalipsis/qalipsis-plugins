package io.qalipsis.plugins.rabbitmq.producer

import io.qalipsis.api.context.StepContext
import io.qalipsis.api.context.StepId
import io.qalipsis.api.context.StepStartStopContext
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.retry.RetryPolicy
import io.qalipsis.api.steps.AbstractStep


/**
 * Implementation of a [io.qalipsis.api.steps.Step] able to produce messages to the RabbitMQ broker.
 *
 * @property rabbitMqProducer producer to use to execute the producing for the current step
 * @property recordFactory closure to generate list of [RabbitMqProducerRecord]
 *
 * @author Alexander Sosnovsky
 */
internal class RabbitMqProducerStep<I>(
    stepId: StepId,
    retryPolicy: RetryPolicy?,
    private val rabbitMqProducer: RabbitMqProducer,
    private val recordFactory: (suspend (ctx: StepContext<*, *>, input: I) -> List<RabbitMqProducerRecord>)
) : AbstractStep<I, I>(stepId, retryPolicy) {

    override suspend fun start(context: StepStartStopContext) {
        rabbitMqProducer.start()
    }

    override suspend fun execute(context: StepContext<I, I>) {
        val input = context.receive()
        val messages = recordFactory(context, input)

        rabbitMqProducer.execute(messages)

        context.send(input)
    }

    override suspend fun stop(context: StepStartStopContext) {
        log.info { "Stopping the RabbitMQ producer" }
        rabbitMqProducer.stop()
        log.info { "RabbitMQ producer was stopped" }
    }

    companion object {

        @JvmStatic
        private val log = logger()
    }

}
