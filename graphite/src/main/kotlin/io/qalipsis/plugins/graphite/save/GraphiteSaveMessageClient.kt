package io.qalipsis.plugins.graphite.save

import io.qalipsis.api.context.StepStartStopContext


/**
 * Client to save messages to Graphite.
 *
 * @author Palina Bril
 */
internal interface GraphiteSaveMessageClient {

    /**
     * Initializes the client and connects to the Graphite server.
     */
    suspend fun start(context: StepStartStopContext)

    /**
     * Inserts messages to the Graphite server.
     */
    suspend fun execute(
        messages: List<String>,
        contextEventTags: Map<String, String>
    ): GraphiteSaveQueryMeters

    /**
     * Cleans the client and closes the connections to the Graphite server.
     */
    suspend fun stop(context: StepStartStopContext)
}
