package io.qalipsis.plugins.graphite.events

import io.qalipsis.plugins.graphite.events.model.GraphiteProtocol

/**
 * @author rklymenko
 */
internal class GraphiteEventsPublisherPickleIntegrationTest :
    AbstractGraphiteEventsPublisherIntegrationTest(GraphiteProtocol.PICKLE)