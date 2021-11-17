package io.qalipsis.plugins.redis.lettuce.streams.consumer

import io.lettuce.core.StreamMessage
import io.micrometer.core.instrument.MeterRegistry
import io.qalipsis.api.Executors
import io.qalipsis.api.annotations.StepConverter
import io.qalipsis.api.events.EventsLogger
import io.qalipsis.api.steps.StepCreationContext
import io.qalipsis.api.steps.StepMonitoringConfiguration
import io.qalipsis.api.steps.StepSpecification
import io.qalipsis.api.steps.StepSpecificationConverter
import io.qalipsis.api.steps.datasource.DatasourceObjectConverter
import io.qalipsis.api.steps.datasource.IterativeDatasourceStep
import io.qalipsis.api.steps.datasource.processors.NoopDatasourceObjectProcessor
import io.qalipsis.plugins.redis.lettuce.configuration.RedisStatefulConnectionFactory
import jakarta.inject.Named
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope

/**
 * [StepSpecificationConverter] from [LettuceStreamsConsumerStepSpecificationImpl] to [LettuceStreamsIterativeReader] for a data
 * source.
 *
 * @author Gabriel Moraes
 */
@StepConverter
internal class LettuceStreamsConsumerStepSpecificationConverter(
    private val meterRegistry: MeterRegistry,
    private val eventsLogger: EventsLogger,
    @Named(Executors.IO_EXECUTOR_NAME) private val ioCoroutineScope: CoroutineScope,
    @Named(Executors.IO_EXECUTOR_NAME) private val ioCoroutineDispatcher: CoroutineDispatcher
) : StepSpecificationConverter<LettuceStreamsConsumerStepSpecificationImpl> {

    override fun support(stepSpecification: StepSpecification<*, *, *>): Boolean {
        return stepSpecification is LettuceStreamsConsumerStepSpecificationImpl
    }

    override suspend fun <I, O> convert(creationContext: StepCreationContext<LettuceStreamsConsumerStepSpecificationImpl>) {
        val spec = creationContext.stepSpecification
        val connectionFactory = suspend {
            RedisStatefulConnectionFactory(spec.connection).create()
        }

        val reader = LettuceStreamsIterativeReader(
            ioCoroutineScope = ioCoroutineScope,
            ioCoroutineDispatcher = ioCoroutineDispatcher,
            connectionFactory = connectionFactory,
            groupName = spec.groupName,
            concurrency = spec.concurrency,
            streamKey = spec.streamKey,
            offset = spec.offset.value,
            eventsLogger = eventsLogger
        )

        val step = IterativeDatasourceStep(
            spec.name,
            reader,
            NoopDatasourceObjectProcessor(),
            buildConverter(spec.monitoringConfig, spec.flattenOutput)
        )
        creationContext.createdStep(step)
    }

    private fun buildConverter(
        monitoringConfig: StepMonitoringConfiguration,
        flattenOutput: Boolean
    ): DatasourceObjectConverter<List<StreamMessage<ByteArray, ByteArray>>, out Any?> {

        return if (flattenOutput) {
            LettuceStreamsConsumerSingleConverter(
                meterRegistry = meterRegistry.takeIf { monitoringConfig.meters })
        } else {
            LettuceStreamsConsumerBatchConverter(
                meterRegistry = meterRegistry.takeIf { monitoringConfig.meters }
            )
        }
    }
}