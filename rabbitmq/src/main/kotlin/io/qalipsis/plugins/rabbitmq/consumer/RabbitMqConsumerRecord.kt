package io.qalipsis.plugins.rabbitmq.consumer

import com.rabbitmq.client.AMQP
import com.rabbitmq.client.Envelope

/**
 * Qalipsis representation of a consumed RabbitMQ record.
 *
 * @author Gabriel Moraes
 *
 * @property offset of the record consumed by Qalipsis.
 * @property exchangeName name of the exchange from where the record was consumed.
 * @property routingKey routing key used by the publisher.
 * @property consumedTimestamp timestamp when the message was consumed by Qalipsis.
 * @property properties of the message.
 * @property value of the record deserialized.
 */
data class RabbitMqConsumerRecord<V>(
        val offset: Long,
        val exchangeName: String,
        val routingKey: String,
        val consumedTimestamp: Long,
        val properties: AMQP.BasicProperties?,
        val value: V
) {
    internal constructor(offset: Long, envelope: Envelope, properties: AMQP.BasicProperties, value: V) : this(
            offset = offset,
            exchangeName = envelope.exchange,
            routingKey = envelope.routingKey,
            consumedTimestamp = System.currentTimeMillis(),
            properties = properties,
            value = value
    )
}