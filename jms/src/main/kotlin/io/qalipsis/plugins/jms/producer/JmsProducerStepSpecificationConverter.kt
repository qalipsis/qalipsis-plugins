package io.qalipsis.plugins.jms.producer

import io.micrometer.core.instrument.MeterRegistry
import io.qalipsis.api.annotations.StepConverter
import io.qalipsis.api.events.EventsLogger
import io.qalipsis.api.steps.StepCreationContext
import io.qalipsis.api.steps.StepSpecification
import io.qalipsis.api.steps.StepSpecificationConverter
import kotlinx.coroutines.ExperimentalCoroutinesApi

/**
 * [StepSpecificationConverter] from [JmsProducerStepSpecificationImpl] to [JmsProducerStep]
 * to use the Produce API.
 *
 * @author Alexander Sosnovsky
 */
@ExperimentalCoroutinesApi
@StepConverter
internal class JmsProducerStepSpecificationConverter(
    private val meterRegistry: MeterRegistry,
    private val eventsLogger: EventsLogger
) : StepSpecificationConverter<JmsProducerStepSpecificationImpl<*>> {

    override fun support(stepSpecification: StepSpecification<*, *, *>): Boolean {
        return stepSpecification is JmsProducerStepSpecificationImpl
    }

    override suspend fun <I, O> convert(creationContext: StepCreationContext<JmsProducerStepSpecificationImpl<*>>) {
        val spec = creationContext.stepSpecification
        val stepId = spec.name
        val producer = JmsProducer(spec.connectionFactory, JmsProducerConverter(),
            eventsLogger = eventsLogger.takeIf { spec.monitoringConfig.events },
            meterRegistry = meterRegistry.takeIf { spec.monitoringConfig.meters }
        )

        @Suppress("UNCHECKED_CAST")
        val step = JmsProducerStep(
            stepId = stepId,
            retryPolicy = spec.retryPolicy,
            recordFactory = spec.recordsFactory,
            jmsProducer = producer,
        )
        creationContext.createdStep(step)
    }
}
