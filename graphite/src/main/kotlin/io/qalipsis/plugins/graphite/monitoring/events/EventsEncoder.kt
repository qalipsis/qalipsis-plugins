/*
 * Copyright 2024 AERIS IT Solutions GmbH
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