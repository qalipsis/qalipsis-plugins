package io.qalipsis.plugins.influxdb.save

import com.influxdb.client.write.Point

/**
 * Wrapper for the result of save points procedure in InfluxDB.
 *
 * @property input the data to save in InfluxDB
 * @property points the data formatted to be able to save in InfluxDB
 * @property meters meters of the save step
 *
 * @author Palina Bril
 */
class InfluxDBSaveResult<I>(
    val input: I,
    val points: List<Point>,
    val meters: InfluxDbSaveQueryMeters
)
