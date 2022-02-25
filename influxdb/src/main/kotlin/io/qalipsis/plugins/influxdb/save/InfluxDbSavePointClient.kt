package io.qalipsis.plugins.influxdb.save

import com.influxdb.client.write.Point
import io.qalipsis.api.context.StepStartStopContext


/**
 * Client to save records to InfluxDb.
 *
 * @author Palina Bril
 */
internal interface InfluxDbSavePointClient {

    /**
     * Initializes the client and connects to the InfluxDB server.
     */
    suspend fun start(context: StepStartStopContext)

    /**
     * Inserts points to the InfluxDB server.
     */
    suspend fun execute(
        bucketName: String, orgName: String, points: List<Point>,
        contextEventTags: Map<String, String>
    ): InfluxDbSaveQueryMeters

    /**
     * Cleans the client and closes the connections to the InfluxDB server.
     */
    suspend fun stop(context: StepStartStopContext)
}
