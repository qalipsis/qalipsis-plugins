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

import com.google.protobuf.Timestamp
import io.aerisconsulting.catadioptre.KTestable
import io.qalipsis.api.events.Event
import io.qalipsis.api.events.EventConverter
import java.io.IOException
import java.io.PrintWriter
import java.io.StringWriter
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import org.apache.commons.text.StringEscapeUtils

/**
 * Implementation of [EventConverter] to generate a [EventOuterClass.Event].
 */
internal class ProtobufEventConverter : EventConverter<EventOuterClass.Event> {

    override fun convert(event: Event): EventOuterClass.Event {
        val protobufEvent = EventOuterClass.Event.newBuilder()
        protobufEvent.name = event.name
        protobufEvent.level = EventOuterClass.Event.Level.valueOf(event.level.name)
        protobufEvent.timestamp = Timestamp.newBuilder().setSeconds(event.timestamp.epochSecond)
            .setNanos(event.timestamp.nano).build()

        event.tags.mapNotNull { (key, value) ->
            when (key) {
                "tenant" -> protobufEvent.tenant = value
                "campaign" -> protobufEvent.campaign = value
                "scenario" -> protobufEvent.scenario = value
                else -> protobufEvent.tagBuilder.putTags(
                    StringEscapeUtils.escapeJson(key).lowercase(),
                    StringEscapeUtils.escapeJson(value)
                )
            }
        }

        event.value?.let { addValue(it, protobufEvent) }
        return protobufEvent.build()
    }

    private fun addValue(value: Any, event: EventOuterClass.Event.Builder) {
        when (value) {
            is String -> event.message = value
            is Boolean -> event.boolean = value
            is Number -> event.number = value.toDouble()
            is Instant -> event.date = Timestamp.newBuilder().setSeconds(value.epochSecond)
                .setNanos(value.nano).build()

            is ZonedDateTime -> {
                val instant = value.toInstant()
                event.date = Timestamp.newBuilder().setSeconds(instant.epochSecond)
                    .setNanos(instant.nano).build()
            }

            is LocalDateTime -> {
                val instant = value.atZone(ZoneId.systemDefault()).toInstant()
                event.date = Timestamp.newBuilder().setSeconds(instant.epochSecond)
                    .setNanos(instant.nano).build()
            }

            is Duration -> event.durationNano = value.toNanos()
            is Throwable -> {
                event.error = value.message ?: value.javaClass.name
                event.stackTrace = stackTraceToString(value)
            }

            is Iterable<*> -> value.filterNotNull().forEach { addValue(it, event) }

            is Array<*> -> value.filterNotNull().forEach { addValue(it, event) }

            else -> Unit
        }
    }

    /**
     * Converts the stack trace of a [Throwable] into a [String].
     */
    @KTestable
    private fun stackTraceToString(throwable: Throwable): String {
        try {
            StringWriter().use { sw ->
                PrintWriter(sw).use { pw ->
                    throwable.printStackTrace(pw)
                    return sw.toString()
                }
            }
        } catch (ioe: IOException) {
            throw IllegalStateException(ioe)
        }
    }
}
