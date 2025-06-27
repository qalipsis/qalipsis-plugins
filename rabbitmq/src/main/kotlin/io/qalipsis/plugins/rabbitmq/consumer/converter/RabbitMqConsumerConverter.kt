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

package io.qalipsis.plugins.rabbitmq.consumer.converter

import com.rabbitmq.client.Delivery
import io.qalipsis.api.context.StepOutput
import io.qalipsis.api.lang.tryAndLogOrNull
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.messaging.deserializer.MessageDeserializer
import io.qalipsis.api.steps.datasource.DatasourceObjectConverter
import io.qalipsis.plugins.rabbitmq.consumer.RabbitMqConsumerRecord
import java.util.concurrent.atomic.AtomicLong

/**
 * Implementation of [DatasourceObjectConverter], that reads a message of native RabbitMQ records and forwards each of
 * them converted as [RabbitMqConsumerRecord].
 *
 * @author Gabriel Moraes
 */
internal class RabbitMqConsumerConverter<V>(
    private val valueDeserializer: MessageDeserializer<V>
) : DatasourceObjectConverter<Delivery, RabbitMqConsumerRecord<V>> {

    override suspend fun supply(
        offset: AtomicLong, value: Delivery,
        output: StepOutput<RabbitMqConsumerRecord<V>>
    ) {
        tryAndLogOrNull(log) {
            output.send(
                RabbitMqConsumerRecord(
                    offset.getAndIncrement(),
                    value.envelope,
                    value.properties,
                    valueDeserializer.deserialize(value.body)
                )
            )
        }
    }

    companion object {
        @JvmStatic
        private val log = logger()
    }
}
