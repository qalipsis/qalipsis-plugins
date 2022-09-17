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

package io.qalipsis.plugins.rabbitmq.consumer

import com.rabbitmq.client.AMQP
import com.rabbitmq.client.Envelope

/**
 * Qalipsis representation of a consumed RabbitMQ record.
 *
 * @author Gabriel Moraes
 *
 * @property offset of the record consumed by Qalipsis.
 * @property exchangeName name of the exchange from where the record was consumed.
 * @property routingKey routing key used by the publisher.
 * @property consumedTimestamp timestamp when the message was consumed by Qalipsis.
 * @property properties of the message.
 * @property value of the record deserialized.
 */
data class RabbitMqConsumerRecord<V>(
        val offset: Long,
        val exchangeName: String,
        val routingKey: String,
        val consumedTimestamp: Long,
        val properties: AMQP.BasicProperties?,
        val value: V
) {
    internal constructor(offset: Long, envelope: Envelope, properties: AMQP.BasicProperties, value: V) : this(
            offset = offset,
            exchangeName = envelope.exchange,
            routingKey = envelope.routingKey,
            consumedTimestamp = System.currentTimeMillis(),
            properties = properties,
            value = value
    )
}