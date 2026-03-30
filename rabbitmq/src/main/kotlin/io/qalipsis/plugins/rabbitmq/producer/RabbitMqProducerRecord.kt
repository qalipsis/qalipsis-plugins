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

package io.qalipsis.plugins.rabbitmq.producer

import com.rabbitmq.client.AMQP


/**
 * Qalipsis representation of a RabbitMQ message to be produced.
 *
 * @author Alexander Sosnovsky
 *
 * @property exchange name of the exchange for which the record will be produced.
 * @property routingKey routing key for publishing.
 * @property props of the message.
 * @property value the payload of the RabbitMQ message.
 */
data class RabbitMqProducerRecord(
    val exchange: String,
    val routingKey: String,
    val props: AMQP.BasicProperties?,
    val value: ByteArray
)
