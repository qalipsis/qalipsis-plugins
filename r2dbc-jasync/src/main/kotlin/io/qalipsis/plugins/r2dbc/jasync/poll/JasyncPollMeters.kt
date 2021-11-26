package io.qalipsis.plugins.r2dbc.jasync.poll

/**
 * Records the metrics for the Jasync poll operation.
 *
 * @property recordsCount counts the number of records sent.
 *
 * @author Alex Averyanov
 */
data class JasyncPollMeters (
    val recordsCount: Int = 0
)