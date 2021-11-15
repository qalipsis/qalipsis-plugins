package io.qalipsis.plugins.jms.producer


/**
 * Qalispsis representation of a Jms Producer result.
 *
 * @property input from the previous steps output.
 *
 * @author Alex Averyanov
 */
data class JmsProducerResult<I>(
    val input: I,
    val metrics: JmsProducerMeters
)