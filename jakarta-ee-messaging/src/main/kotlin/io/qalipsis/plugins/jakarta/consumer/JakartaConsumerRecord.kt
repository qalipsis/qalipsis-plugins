/*
 * QALIPSIS
 * Copyright (C) 2025 AERIS IT Solutions GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package io.qalipsis.plugins.jakarta.consumer


import jakarta.jms.Destination
import jakarta.jms.Message

/**
 * Qalipsis representation of a consumed Jakarta message.
 *
 * @author Krawist Ngoben
 *
 * @property destination name of the topic or queue from where the message was consumed
 * @property offset offset of the record relatively to the Qalipsis consumer
 * @property messageId uniquely identifies each message sent by a Jakarta provider
 * @property correlationId either provider-specific message IDs or application-specific String values
 * @property priority the Jakarta API defines ten levels of priority value, with 0 as the lowest priority and 9 as the highest. In addition, clients should consider priorities 0-4 as gradations of normal priority and priorities 5-9 as gradations of expedited priority
 * @property expiration this is the difference, measured in milliseconds, between the expiration time and midnight, January 1, 1970 UTC.
 * @property deliveredTime delivery time is the earliest time when a JMS provider may deliver the message to a consumer
 * @property timestamp the time a message was handed off to a provider to be sent
 * @property value of the record deserialized
 */
data class JakartaConsumerRecord<T>(
    val destination: Destination?,
    val offset: Long?,
    val messageId: String? = null,
    val correlationId: String? = null,
    val priority: Int? = null,
    val expiration: Long? = null,
    val deliveredTime: Long? = null,
    val timestamp: Long? = null,
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
