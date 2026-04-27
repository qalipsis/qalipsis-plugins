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

package io.qalipsis.plugins.kafka.events

import io.micronaut.context.annotation.Requires
import io.qalipsis.api.events.Event
import jakarta.inject.Singleton

/**
 * Implementation of [EventKafkaSerializer] using protobuf.
 * It first converts the event to a protobuf object before serializing.
 */
@Singleton
@Requires(property = "events.export.kafka.serializer", value = "protobuf")
internal class ProtobufEventSerializer : EventKafkaSerializer {

    private val protobufEventConverter = ProtobufEventConverter()

    override fun serialize(topic: String, data: Event): ByteArray {
        return protobufEventConverter.convert(data).toByteArray()
    }
}
