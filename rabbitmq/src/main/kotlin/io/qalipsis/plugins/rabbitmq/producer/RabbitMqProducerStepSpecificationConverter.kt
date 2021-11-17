package io.qalipsis.plugins.rabbitmq.producer

import com.rabbitmq.client.ConnectionFactory
import io.aerisconsulting.catadioptre.KTestable
import io.micrometer.core.instrument.MeterRegistry
import io.qalipsis.api.annotations.StepConverter
import io.qalipsis.api.events.EventsLogger
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
    private val meterRegistry: MeterRegistry,
    private val eventsLogger: EventsLogger
) : StepSpecificationConverter<RabbitMqProducerStepSpecificationImpl<*>> {

    override fun support(stepSpecification: StepSpecification<*, *, *>): Boolean {
        return stepSpecification is RabbitMqProducerStepSpecificationImpl
    }

    override suspend fun <I, O> convert(creationContext: StepCreationContext<RabbitMqProducerStepSpecificationImpl<*>>) {
        val spec = creationContext.stepSpecification
        val stepId = spec.name
        val producer = RabbitMqProducer(4, buildConnectionFactory(spec.connectionConfiguration))
        val rabbitMqProducerResult = RabbitMqProducerResult()

        @Suppress("UNCHECKED_CAST")
        val step = RabbitMqProducerStep(
            stepId = stepId,
            retryPolicy = spec.retryPolicy,
            recordFactory = spec.recordsFactory,
            rabbitMqProducer = producer,
            rabbitMqProducerResult = rabbitMqProducerResult,
            eventsLogger = eventsLogger.takeIf { spec.monitoring.events },
            meterRegistry = meterRegistry.takeIf { spec.monitoring.meters }
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

    companion object {

        /**
         * Timeout used to connect to the RabbitMQ broker.
         */
        @JvmStatic
        private val DEFAULT_CONNECTION_TIMEOUT = Duration.ofSeconds(30).toMillis().toInt()
    }

}
