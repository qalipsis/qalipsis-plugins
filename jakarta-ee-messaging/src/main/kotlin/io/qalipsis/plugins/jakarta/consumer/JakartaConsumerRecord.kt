/*
 * Copyright 2022 AERIS IT Solutions GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing
 * permissions and limitations under the License.
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
