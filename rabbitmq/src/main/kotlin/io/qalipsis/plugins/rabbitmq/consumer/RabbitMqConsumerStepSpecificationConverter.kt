package io.qalipsis.plugins.rabbitmq.consumer

import com.rabbitmq.client.ConnectionFactory
import com.rabbitmq.client.Delivery
import io.aerisconsulting.catadioptre.KTestable
import io.micrometer.core.instrument.MeterRegistry
import io.qalipsis.api.annotations.StepConverter
import io.qalipsis.api.context.StepName
import io.qalipsis.api.events.EventsLogger
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
    private val eventsLogger: EventsLogger,
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
            buildConnectionFactory(spec.connectionConfiguration),
            meterRegistry.takeIf { spec.monitoringConfig.meters },
            eventsLogger.takeIf { spec.monitoringConfig.events }
        )

        val step = IterativeDatasourceStep(
            stepId,
            reader,
            NoopDatasourceObjectProcessor(),
            buildConverter(stepId, spec.valueDeserializer)
        )
        creationContext.createdStep(step)
    }

    @KTestable
    fun buildConnectionFactory(connectionConfiguration: RabbitMqConnectionConfiguration): ConnectionFactory {
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
    fun buildConverter(
        stepName: StepName,
        valueDeserializer: MessageDeserializer<*>
    ): DatasourceObjectConverter<Delivery, out Any?> {

        return RabbitMqConsumerConverter(
            valueDeserializer as MessageDeserializer<Any>
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
