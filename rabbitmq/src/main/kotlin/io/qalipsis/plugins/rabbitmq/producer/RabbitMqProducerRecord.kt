package io.qalipsis.plugins.rabbitmq.producer

import com.rabbitmq.client.AMQP


/**
 * Qalipsis representation of a RabbitMQ message to be produced.
 *
 * @author Alexander Sosnovsky
 *
 * @property exchange name of the exchange for which the record will be produced.
 * @property routingKey routing key for publishing.
 * @property props of the message.
 * @property value the payload of the RabbitMQ message.
 */
data class RabbitMqProducerRecord(
    val exchange: String,
    val routingKey: String,
    val props: AMQP.BasicProperties?,
    val value: ByteArray
)
