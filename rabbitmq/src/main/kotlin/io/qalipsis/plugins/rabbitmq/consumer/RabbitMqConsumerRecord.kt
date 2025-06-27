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