package io.qalipsis.plugins.redis.lettuce.save

import io.micrometer.core.instrument.MeterRegistry
import io.qalipsis.api.Executors
import io.qalipsis.api.annotations.StepConverter
import io.qalipsis.api.events.EventsLogger
import io.qalipsis.api.steps.StepCreationContext
import io.qalipsis.api.steps.StepSpecification
import io.qalipsis.api.steps.StepSpecificationConverter
import io.qalipsis.api.sync.asSuspended
import io.qalipsis.plugins.redis.lettuce.configuration.RedisStatefulConnectionFactory
import jakarta.inject.Named
import java.time.Duration
import kotlin.coroutines.CoroutineContext

/**
 * [StepSpecificationConverter] from [LettuceSaveStepSpecificationImpl] to [LettuceSaveStep].
 *
 * @author Gabriel Moraes
 */
@StepConverter
internal class LettuceSaveStepSpecificationConverter(
    private val eventsLogger: EventsLogger,
    private val meterRegistry: MeterRegistry,
    @Named(Executors.IO_EXECUTOR_NAME) private val ioCoroutineContext: CoroutineContext
) : StepSpecificationConverter<LettuceSaveStepSpecificationImpl<*>> {

    override fun support(stepSpecification: StepSpecification<*, *, *>): Boolean {
        return stepSpecification is LettuceSaveStepSpecificationImpl<*>
    }

    override suspend fun <I, O> convert(creationContext: StepCreationContext<LettuceSaveStepSpecificationImpl<*>>) {
        val spec = creationContext.stepSpecification
        val connectionFactory = suspend {
            RedisStatefulConnectionFactory(spec.connectionConfiguration).create().asSuspended().get(CONNECTION_TIMEOUT)
        }

        val step = LettuceSaveStep(
            id = spec.name,
            retryPolicy = spec.retryPolicy,
            ioCoroutineContext = ioCoroutineContext,
            connectionFactory = connectionFactory,
            recordsFactory = spec.recordsFactory,
            eventsLogger = eventsLogger.takeIf { spec.monitoringConfig.events },
            meterRegistry = meterRegistry.takeIf { spec.monitoringConfig.meters }
        )

        creationContext.createdStep(step)
    }

    companion object {
        private val CONNECTION_TIMEOUT = Duration.ofSeconds(10)
    }
}