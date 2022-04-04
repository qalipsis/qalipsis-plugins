package io.qalipsis.plugins.graphite.save

/**
 * Wrapper for the result of save messages procedure in Graphite.
 *
 * @property input the data to save in Graphite
 * @property messages the data formatted to be able to save in Graphite
 * @property meters meters of the save step
 *
 * @author Palina Bril
 */
class GraphiteSaveResult<I>(
    val input: I,
    val messages: List<String>,
    val meters: GraphiteSaveQueryMeters
)
