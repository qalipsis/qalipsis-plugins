package io.qalipsis.plugins.jms.consumer

import javax.jms.Destination
import javax.jms.Message

/**
 * Qalipsis representation of a consumed JMS message.
 *
 * @author Alexander Sosnovsky
 *
 * @property destination name of the topic or queue from where the message was consumed
 * @property offset offset of the record relatively to the Qalipsis consumer
 * @property messageId uniquely identifies each message sent by a JMS provider
 * @property correlationId either provider-specific message IDs or application-specific String values
 * @property priority the JMS API defines ten levels of priority value, with 0 as the lowest priority and 9 as the highest. In addition, clients should consider priorities 0-4 as gradations of normal priority and priorities 5-9 as gradations of expedited priority
 * @property expiration this is the difference, measured in milliseconds, between the expiration time and midnight, January 1, 1970 UTC.
 * @property deliveredTime delivery time is the earliest time when a JMS provider may deliver the message to a consumer
 * @property timestamp the time a message was handed off to a provider to be sent
 * @property value of the record deserialized
 */
data class JmsConsumerRecord<T>(
    val destination: Destination,
    val offset: Long,
    val messageId: String?,
    val correlationId: String?,
    val priority: Int?,
    val expiration: Long?,
    val deliveredTime: Long?,
    val timestamp: Long?,
    val value: T
) {
    internal constructor(
        consumedOffset: Long, message: Message, value: T
    ) : this(
        destination = message.jmsDestination,
        offset = consumedOffset,
        messageId = kotlin.runCatching { message.jmsMessageID }.getOrNull(),
        correlationId = kotlin.runCatching { message.jmsCorrelationID }.getOrNull(),
        priority = kotlin.runCatching { message.jmsPriority }.getOrNull(),
        expiration = kotlin.runCatching { message.jmsExpiration }.getOrNull(),
        timestamp = kotlin.runCatching { message.jmsTimestamp }.getOrNull(),
        deliveredTime = kotlin.runCatching { message.jmsDeliveryTime }.getOrNull(),
        value = value
    )
}
