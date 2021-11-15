package io.qalipsis.plugins.rabbitmq.consumer

import com.rabbitmq.client.ConnectionFactory
import com.rabbitmq.client.Delivery
import io.aerisconsulting.catadioptre.KTestable
import io.micrometer.core.instrument.MeterRegistry
import io.qalipsis.api.annotations.StepConverter
import io.qalipsis.api.context.StepId
import io.qalipsis.api.lang.supplyIf
import io.qalipsis.api.messaging.deserializer.MessageDeserializer
import io.qalipsis.api.steps.StepCreationContext
import io.qalipsis.api.steps.StepSpecification
import io.qalipsis.api.steps.StepSpecificationConverter
import io.qalipsis.api.steps.datasource.DatasourceObjectConverter
import io.qalipsis.api.steps.datasource.IterativeDatasourceStep
import io.qalipsis.api.steps.datasource.processors.NoopDatasourceObjectProcessor
import io.qalipsis.plugins.rabbitmq.configuration.RabbitMqConnectionConfiguration
import io.qalipsis.plugins.rabbitmq.consumer.converter.RabbitMqConsumerConverter
import java.time.Duration

/**
 * [StepSpecificationConverter] from [RabbitMqConsumerStepSpecificationImpl] to [RabbitMqConsumerIterativeReader] for a data
 * source.
 *
 * @author Gabriel Moraes
 */
@StepConverter
internal class RabbitMqConsumerStepSpecificationConverter(
    private val meterRegistry: MeterRegistry
) : StepSpecificationConverter<RabbitMqConsumerStepSpecificationImpl<*>> {

    override fun support(stepSpecification: StepSpecification<*, *, *>): Boolean {
        return stepSpecification is RabbitMqConsumerStepSpecificationImpl<*>
    }

    override suspend fun <I, O> convert(creationContext: StepCreationContext<RabbitMqConsumerStepSpecificationImpl<*>>) {
        val spec = creationContext.stepSpecification

        val stepId = spec.name
        val reader = RabbitMqConsumerIterativeReader(
            spec.concurrency,
            spec.prefetchCount,
            spec.queueName,
            buildConnectionFactory(spec.connectionConfiguration)
        )

        val step = IterativeDatasourceStep(
            stepId,
            reader,
            NoopDatasourceObjectProcessor(),
            buildConverter(stepId, spec.valueDeserializer, spec.metrics)
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

    private fun buildConverter(
        stepId: StepId,
        valueDeserializer: MessageDeserializer<*>,
        metricsConfiguration: RabbitMqConsumerMetricsConfiguration
    ): DatasourceObjectConverter<Delivery, out Any?> {

        val consumedValueBytesCounter = supplyIf(metricsConfiguration.valuesBytesCount) {
            meterRegistry.counter("rabbitmq-consumer-value-bytes", "step", stepId)
        }

        val consumedRecordsCounter = supplyIf (metricsConfiguration.recordsCount) {
            meterRegistry.counter("rabbitmq-consumer-records", "step", stepId)
        }

        return RabbitMqConsumerConverter(
            valueDeserializer,
            consumedValueBytesCounter,
            consumedRecordsCounter
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
