package io.qalipsis.plugins.jms.consumer

/**
 * Records the metrics for the JMS consumer.
 *
 * @property record received records
 * @property meters meters of the consumption of [record]
 *
 * @author Alex Averyanov
 */

class JmsConsumerResult<T>(
    val record: JmsConsumerRecord<T>,
    val meters: JmsConsumerMeters
)