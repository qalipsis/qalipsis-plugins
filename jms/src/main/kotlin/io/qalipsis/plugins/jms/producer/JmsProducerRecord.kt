package io.qalipsis.plugins.jms.producer

import javax.jms.Destination

/**
 * Qalipsis representation of a JMS message to be produced.
 *
 * @author Alexander Sosnovsky
 *
 * @property destination name of the topic or queue where message should be produced
 * @property messageType it is a type used for creating native JMS [Message]
 * @property value the payload of the [Message]
 */
data class JmsProducerRecord(
    val destination: Destination,
    val messageType: JmsMessageType = JmsMessageType.AUTO,
    val value: Any
)
