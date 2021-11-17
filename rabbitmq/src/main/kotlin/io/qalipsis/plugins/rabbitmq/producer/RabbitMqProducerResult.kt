package io.qalipsis.plugins.rabbitmq.producer

/**
 * @author rklymenko
 *
 *  Configuration of the metrics to record for the RabbitMQ.
 *
 * @property records to store input value.
 * @property error to store possible exception.
 * @property monitoring to collect statistics.
 *
 */
data class RabbitMqProducerResult(
    var records: List<Any>? = null
)