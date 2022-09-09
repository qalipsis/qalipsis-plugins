package io.qalipsis.plugins.influxdb.search

import io.qalipsis.api.context.StepStartStopContext
import io.qalipsis.plugins.influxdb.poll.InfluxDbQueryResult

/**
 * Client to query from InfluxDb.
 *
 * @author Palina Bril
 */
interface InfluxDbQueryClient {

    /**
     * Executes a query and returns the list of results.
     */
    suspend fun execute(query: String, contextEventTags: Map<String, String>): InfluxDbQueryResult

    /**
     * Initiate the meters if they are enabled.
     */
    suspend fun start(context: StepStartStopContext)

    /**
     * Cleans the client and closes the connections to the InfluxDB server.
     */
    suspend fun stop(context: StepStartStopContext)
}


