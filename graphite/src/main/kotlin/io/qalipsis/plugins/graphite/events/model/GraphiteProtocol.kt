package io.qalipsis.plugins.graphite.events.model

/**
 * Enumerates supported [graphite][https://github.com/graphite-project] transport protocols.
 *
 * @author rklymenko
 */
internal enum class GraphiteProtocol {
    PLAINTEXT, PICKLE
}