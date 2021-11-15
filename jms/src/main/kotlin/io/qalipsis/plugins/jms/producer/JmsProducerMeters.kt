package io.qalipsis.plugins.jms.producer

/**
 * Records the metrics for the JMS producer.
 *
 * @property recordsToProduce counts the number of records to be sent.
 * @property producedBytes records the number of bytes sent.
 * @property producedRecords counts the number of records actually sent.
 *
 * @author Alex Averyanov
 */
data class JmsProducerMeters(
    var recordsToProduce: Int = 0,
    var producedBytes: Int = 0,
    var producedRecords: Int = 0
)