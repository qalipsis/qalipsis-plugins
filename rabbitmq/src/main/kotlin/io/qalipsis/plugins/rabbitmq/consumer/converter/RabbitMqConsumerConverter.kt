package io.qalipsis.plugins.rabbitmq.consumer.converter

import com.rabbitmq.client.Delivery
import io.qalipsis.api.context.StepOutput
import io.qalipsis.api.lang.tryAndLogOrNull
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.messaging.deserializer.MessageDeserializer
import io.qalipsis.api.steps.datasource.DatasourceObjectConverter
import io.qalipsis.plugins.rabbitmq.consumer.RabbitMqConsumerRecord
import java.util.concurrent.atomic.AtomicLong

/**
 * Implementation of [DatasourceObjectConverter], that reads a message of native RabbitMQ records and forwards each of
 * them converted as [RabbitMqConsumerRecord].
 *
 * @author Gabriel Moraes
 */
internal class RabbitMqConsumerConverter<V>(
    private val valueDeserializer: MessageDeserializer<V>
) : DatasourceObjectConverter<Delivery, RabbitMqConsumerRecord<V>> {

    override suspend fun supply(
        offset: AtomicLong, value: Delivery,
        output: StepOutput<RabbitMqConsumerRecord<V>>
    ) {
        tryAndLogOrNull(log) {
            output.send(
                RabbitMqConsumerRecord(
                    offset.getAndIncrement(),
                    value.envelope,
                    value.properties,
                    valueDeserializer.deserialize(value.body)
                )
            )
        }
    }

    companion object {
        @JvmStatic
        private val log = logger()
    }
}
