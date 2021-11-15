package io.qalipsis.plugins.redis.lettuce.streams.producer

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
 * [StepSpecificationConverter] from [LettuceStreamsProducerStepSpecificationImpl] to [LettuceStreamsProducerStep].
 *
 * @author Gabriel Moraes
 */
@StepConverter
internal class LettuceStreamsProducerStepSpecificationConverter(
    private val eventsLogger: EventsLogger,
    private val meterRegistry: MeterRegistry,
    @Named(Executors.IO_EXECUTOR_NAME) private val ioCoroutineContext: CoroutineContext
) : StepSpecificationConverter<LettuceStreamsProducerStepSpecificationImpl<*>> {

    override fun support(stepSpecification: StepSpecification<*, *, *>): Boolean {
        return stepSpecification is LettuceStreamsProducerStepSpecificationImpl<*>
    }

    override suspend fun <I, O> convert(creationContext: StepCreationContext<LettuceStreamsProducerStepSpecificationImpl<*>>) {
        val spec = creationContext.stepSpecification
        val connectionFactory = suspend {
            RedisStatefulConnectionFactory(spec.connection).create().asSuspended().get(
                CONNECTION_TIMEOUT
            )
        }

        val step = LettuceStreamsProducerStep(
            id = spec.name,
            retryPolicy = spec.retryPolicy,
            ioCoroutineContext = ioCoroutineContext,
            connectionFactory = connectionFactory,
            recordsFactory = spec.recordsFactory,
            eventsLogger = eventsLogger.takeIf { spec.monitoringConfiguration.events },
            meterRegistry = meterRegistry.takeIf { spec.monitoringConfiguration.meters }
        )

        creationContext.createdStep(step)
    }

    companion object {
        private val CONNECTION_TIMEOUT = Duration.ofSeconds(10)
    }
}