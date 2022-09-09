package io.qalipsis.plugins.influxdb.search

import com.influxdb.query.FluxRecord
import io.qalipsis.plugins.influxdb.poll.InfluxDbQueryMeters

/**
 * A wrapper for the input for search, meters and documents.
 *
 * @property input input value used to generate the search query
 * @property results result of search query procedure in InfluxDb
 * @property meters meters of the query
 *
 * @author Eric Jessé
 */
class InfluxDbSearchResult<I>(
    val input: I,
    val results: List<FluxRecord>,
    val meters: InfluxDbQueryMeters
)
