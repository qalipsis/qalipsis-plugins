package io.qalipsis.plugins.graphite.events

import io.qalipsis.plugins.graphite.events.model.GraphiteProtocol

/**
 * @author rklymenko
 */
internal class GraphiteEventsPublisherPlaintextIntegrationTest :
    AbstractGraphiteEventsPublisherIntegrationTest(GraphiteProtocol.PLAINTEXT)