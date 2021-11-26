package io.qalipsis.plugins.r2dbc.jasync.search

import java.time.Duration

/**
 * Records the metrics for the Jasync search operation.
 *
 * @property recordsCounter counts the number of records searched.
 * @property timeToResponse the time that takes on a being search a records to database.
 *
 * @author Alex Averyanov
 */
data class JasyncSearchMeters (
    val recordsCounter: Int = 0,
    val timeToResponse: Duration
)