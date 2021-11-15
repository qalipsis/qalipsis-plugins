package io.qalipsis.plugins.jms.producer

import io.micrometer.core.instrument.Counter

/**
 * Wrapper for the meters of the JMS producer operations.
 *
 * @author Alexander Sosnovsky
 */
internal data class JmsProducerMetrics(
    private val producedBytesCounter: Counter? = null,
    private val producedRecordsCounter: Counter? = null,
) {
    /**
     * Records the number of sent bytes.
     */
    fun countBytes(size: Double) = producedBytesCounter?.increment(size)

    /**
     * Records the number of sent records.
     */
    fun countRecord() = producedRecordsCounter?.increment()

}
