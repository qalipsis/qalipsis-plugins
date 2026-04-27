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
