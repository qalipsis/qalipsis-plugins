package io.qalipsis.plugins.jms.consumer

/**
 * Records the metrics for the JMS consumer.
 *
 * @property consumedBytes records the number of bytes get.
 *
 * @author Alex Averyanov
 */
data class JmsConsumerMeters(
    var consumedBytes: Int = 0
)