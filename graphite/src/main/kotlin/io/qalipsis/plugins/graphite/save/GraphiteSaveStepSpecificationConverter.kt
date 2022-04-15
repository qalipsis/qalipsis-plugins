package io.qalipsis.plugins.graphite.save

import io.micrometer.core.instrument.MeterRegistry
import io.qalipsis.api.Executors
import io.qalipsis.api.annotations.StepConverter
import io.qalipsis.api.events.EventsLogger
import io.qalipsis.api.lang.supplyIf
import io.qalipsis.api.steps.StepCreationContext
import io.qalipsis.api.steps.StepSpecification
import io.qalipsis.api.steps.StepSpecificationConverter
import jakarta.inject.Named
import kotlinx.coroutines.CoroutineScope

/**
 * [StepSpecificationConverter] from [GraphiteSaveStepSpecificationImpl] to [GraphiteSaveStep]
 * to use the Save API.
 *
 * @author Palina Bril
 */
@StepConverter
internal class GraphiteSaveStepSpecificationConverter(
    private val meterRegistry: MeterRegistry,
    private val eventsLogger: EventsLogger,
    @Named(Executors.IO_EXECUTOR_NAME) private val coroutineScope: CoroutineScope,
) : StepSpecificationConverter<GraphiteSaveStepSpecificationImpl<*>> {

    override fun support(stepSpecification: StepSpecification<*, *, *>): Boolean {
        return stepSpecification is GraphiteSaveStepSpecificationImpl
    }

    override suspend fun <I, O> convert(creationContext: StepCreationContext<GraphiteSaveStepSpecificationImpl<*>>) {
        val spec = creationContext.stepSpecification
        val stepId = spec.name
        val workerGroup = spec.connectionConfig.workerGroup()
        val clientBuilder = {
            GraphiteSaveClient(
                host = spec.connectionConfig.host,
                port = spec.connectionConfig.port,
                workerGroup = workerGroup,
                coroutineScope = coroutineScope
            )
        }

        @Suppress("UNCHECKED_CAST")
        val step = GraphiteSaveStep(
            id = stepId,
            retryPolicy = spec.retryPolicy,
            graphiteSaveMessageClient = GraphiteSaveMessageClientImpl(
                clientBuilder = clientBuilder,
                eventsLogger = supplyIf(spec.monitoringConfig.events) { eventsLogger },
                meterRegistry = supplyIf(spec.monitoringConfig.meters) { meterRegistry }
            ),
            messageFactory = spec.records
        )
        creationContext.createdStep(step)
    }
}
