package io.qalipsis.plugins.influxdb.poll

import java.time.Duration

/**
 * Meters of the performed query.
 *
 * @property fetchedRecords count of received records
 * @property timeToResult elapsed time from the query execution until the complete successful reception of results
 *
 * @author Alex Averyanov
 */
data class InfluxDbQueryMeters(
    val fetchedRecords: Int,
    val timeToResult: Duration
)
