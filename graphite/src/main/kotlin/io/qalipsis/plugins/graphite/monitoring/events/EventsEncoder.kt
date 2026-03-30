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

package io.qalipsis.plugins.graphite.monitoring.events

import io.netty.channel.ChannelHandler.Sharable
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.MessageToMessageEncoder
import io.qalipsis.api.events.Event
import io.qalipsis.plugins.graphite.client.GraphiteRecord

/**
 * Implementation of [MessageToMessageEncoder] to convert the [Event]s into [io.qalipsis.plugins.graphite.client.GraphiteRecord]s.
 *
 * @property batchSize the number of events to map to a single message
 *
 * @author Eric Jessé.
 *
 */
@Sharable
internal class EventsEncoder(private val prefix: String, private val batchSize: Int = 100) :
    MessageToMessageEncoder<List<Event>>() {

    override fun encode(ctx: ChannelHandlerContext, msg: List<Event>, out: MutableList<Any>) {
        msg.windowed(batchSize, batchSize, true).forEach { events ->
            out.add(
                events.map { event ->
                    // Graphite only supports records with numbers as values. Other records
                    // are kept but with null as value.
                    val value = event.value as? Number ?: 0
                    val tags = mutableMapOf<String, String>()
                    event.tags.forEach { (key, value) ->
                        tags[key] = value
                    }
                    tags["level"] = event.level.name.lowercase()
                    GraphiteRecord("${prefix}${event.name}", event.timestamp, value, tags)
                }
            )
        }
    }

}