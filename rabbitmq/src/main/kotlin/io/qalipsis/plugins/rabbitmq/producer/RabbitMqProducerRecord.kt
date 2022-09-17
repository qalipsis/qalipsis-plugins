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
