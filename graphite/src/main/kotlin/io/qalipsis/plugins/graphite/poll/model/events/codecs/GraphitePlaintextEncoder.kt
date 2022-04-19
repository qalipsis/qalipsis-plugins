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

package io.qalipsis.plugins.graphite.poll.model.events.codecs

import io.aerisconsulting.catadioptre.KTestable
import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.MessageToByteEncoder
import io.qalipsis.api.events.Event
import io.qalipsis.api.logging.LoggerHelper.logger

/**
 * Implementation of [MessageToByteEncoder] for [graphite][https://github.com/graphite-project] plaintext protocol.
 * Receives a list of [Event], encodes them to pickle format and then sends it to [ChannelHandlerContext] for further processing.
 *
 * @author rklymenko
 */
internal class GraphitePlaintextEncoder : MessageToByteEncoder<List<Event>>() {

    /**
     * Encodes incoming list of [Event] to [plaintext][https://graphite.readthedocs.io/en/latest/feeding-carbon.html#the-plaintext-protocol]
     * Sends encoded messages one by one to [ChannelHandlerContext]
     */
    @KTestable
    public override fun encode(
        ctx: ChannelHandlerContext,
        events: List<Event>, out: ByteBuf
    ) {
        out.retain()
        log.trace { "Encoding events: $events" }
        events.forEach {
            out.writeBytes(convertToPlaintext(it).encodeToByteArray())
        }
        ctx.writeAndFlush(out).addListener {
            if (!it.isSuccess) {
                log.debug(it.cause()) { " Failed to send $events with the plaintext encoder" }
            } else {
                log.trace { "Events flushed: $events" }
            }
        }
    }

    /**
     * Receives an [Event] and encodes it to [plaintext][https://graphite.readthedocs.io/en/latest/feeding-carbon.html#the-plaintext-protocol]
     */
    @KTestable
    fun convertToPlaintext(event: Event): String {
        val payload = StringBuilder()
        payload.append(event.name)
        for (tag in event.tags) {
            payload.append(SEMICOLON)
            payload.append(tag.key)
            payload.append(EQ)
            payload.append(tag.value)
        }
        payload.append(" ")
        payload.append(event.value)
        payload.append(" ")
        payload.append(event.timestamp.toEpochMilli() / 1000)
        payload.append("\n")
        return payload.toString()
    }

    companion object {

        private const val SEMICOLON = ";"
        private const val EQ = "="

        private val log = logger()
    }
}