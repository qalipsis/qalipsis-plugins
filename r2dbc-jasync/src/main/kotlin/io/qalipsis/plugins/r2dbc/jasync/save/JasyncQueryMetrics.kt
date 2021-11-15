package io.qalipsis.plugins.r2dbc.jasync.save

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.Timer
import java.util.concurrent.TimeUnit

/**
 * Wrapper for the meters of the Jasync save operations.
 *
 * @author Carlos Vieira
 */
data class JasyncQueryMetrics(
        val recordsCount: Counter? = null,
        val timeToSuccess: Timer? = null,
        val timeToFailure: Timer? = null,
        val successCounter: Counter? = null,
        val failureCounter: Counter? = null,
) {
    /**
     * Records the number of send records.
     */
    fun countRecords(size: Int) = recordsCount?.increment(size.toDouble())

    /**
     * Records the time to response when the request is successful.
     */
    fun recordTimeToSuccess(durationNanos: Long) = timeToSuccess?.record(durationNanos, TimeUnit.NANOSECONDS)

    /**
     * Records the time to response when the request fails.
     */
    fun recordTimeToFailure(durationNanos: Long) = timeToFailure?.record(durationNanos, TimeUnit.NANOSECONDS)

    /**
     * Records a new success.
     */
    fun countSuccess() = successCounter?.increment()

    /**
     * Records a new failure.
     */
    fun countFailure() = failureCounter?.increment()
}
