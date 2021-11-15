package io.qalipsis.plugins.rabbitmq.producer

import com.rabbitmq.client.ConnectionFactory
import io.aerisconsulting.catadioptre.KTestable
import io.micrometer.core.instrument.MeterRegistry
import io.qalipsis.api.annotations.StepConverter
import io.qalipsis.api.context.StepName
import io.qalipsis.api.lang.supplyIf
import io.qalipsis.api.steps.StepCreationContext
import io.qalipsis.api.steps.StepSpecification
import io.qalipsis.api.steps.StepSpecificationConverter
import io.qalipsis.plugins.rabbitmq.configuration.RabbitMqConnectionConfiguration
import kotlinx.coroutines.ExperimentalCoroutinesApi
import java.time.Duration

/**
 * [StepSpecificationConverter] from [RabbitMqProducerStepSpecificationImpl] to [RabbitMqProducerStep]
 * to use the Produce API.
 *
 * @author Alexander Sosnovsky
 */
@ExperimentalCoroutinesApi
@StepConverter
internal class RabbitMqProducerStepSpecificationConverter(
    private val meterRegistry: MeterRegistry
) : StepSpecificationConverter<RabbitMqProducerStepSpecificationImpl<*>> {

    override fun support(stepSpecification: StepSpecification<*, *, *>): Boolean {
        return stepSpecification is RabbitMqProducerStepSpecificationImpl
    }

    override suspend fun <I, O> convert(creationContext: StepCreationContext<RabbitMqProducerStepSpecificationImpl<*>>) {
        val spec = creationContext.stepSpecification
        val stepId = spec.name
        val metrics = buildMetrics(spec.metrics, stepId)
        val producer = RabbitMqProducer(4, buildConnectionFactory(spec.connectionConfiguration), metrics)

        @Suppress("UNCHECKED_CAST")
        val step = RabbitMqProducerStep<String>(
            stepId = stepId,
            retryPolicy = spec.retryPolicy,
            recordFactory = spec.recordsFactory,
            rabbitMqProducer = producer
        )
        creationContext.createdStep(step)
    }

    @KTestable
    private fun buildConnectionFactory(connectionConfiguration: RabbitMqConnectionConfiguration): ConnectionFactory {
        val connectionFactory = ConnectionFactory()

        connectionFactory.host = connectionConfiguration.host
        connectionFactory.port = connectionConfiguration.port
        connectionFactory.username = connectionConfiguration.username
        connectionFactory.password = connectionConfiguration.password

        connectionFactory.virtualHost = connectionConfiguration.virtualHost
        connectionFactory.clientProperties = connectionConfiguration.clientProperties
        connectionFactory.connectionTimeout = DEFAULT_CONNECTION_TIMEOUT
        connectionFactory.useNio()

        return connectionFactory
    }

    @KTestable
    private fun buildMetrics(metrics: RabbitMqProducerMetricsConfiguration, stepId: StepName): RabbitMqProducerMetrics {
        val producedBytesCounter = supplyIf(metrics.bytesCount) {
            meterRegistry.counter("rabbitmq-producer-sent-bytes", "step", stepId)
        }
        val producedRecordsCounter = supplyIf(metrics.recordsCount) {
            meterRegistry.counter("rabbitmq-producer-records", "step", stepId)
        }

        return RabbitMqProducerMetrics(
            producedBytesCounter = producedBytesCounter,
            producedRecordsCounter = producedRecordsCounter
        )
    }

    companion object {

        /**
         * Timeout used to connect to the RabbitMQ broker.
         */
        @JvmStatic
        private val DEFAULT_CONNECTION_TIMEOUT = Duration.ofSeconds(30).toMillis().toInt()
    }

}
