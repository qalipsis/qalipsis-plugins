package io.qalipsis.plugins.influxdb.poll

import com.influxdb.query.FluxRecord

/**
 * Wrapper for the result of poll in InfluxDb.
 *
 * @property results list of InfluxDb records.
 * @property meters of the poll step.
 *
 * @author Alex Averyanov
 */
class InfluxDbPollResults(
    val results: List<FluxRecord>,
    val meters: InfluxDbQueryMeters
) : Iterable<FluxRecord> {

    override fun iterator(): Iterator<FluxRecord> {
        return results.iterator()
    }
}
