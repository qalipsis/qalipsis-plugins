/*
 * QALIPSIS
 * Copyright (C) 2025 AERIS IT Solutions GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package io.qalipsis.plugins.kafka.events

import io.micronaut.context.annotation.Requires
import io.qalipsis.api.events.Event
import io.qalipsis.api.events.EventJsonConverter
import jakarta.inject.Singleton
import java.nio.charset.StandardCharsets

/**
 * Implementation of [EventKafkaSerializer] using JSON.
 */
@Singleton
@Requires(property = "events.export.kafka.serializer", value = "json", defaultValue = "json")
internal class JsonEventSerializer(
    private val eventsConverter: EventJsonConverter,
) : EventKafkaSerializer {

    override fun serialize(topic: String, data: Event): ByteArray {
        return eventsConverter.convert(data).toByteArray(StandardCharsets.UTF_8)
    }

}
