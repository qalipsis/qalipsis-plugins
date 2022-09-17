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

package io.qalipsis.plugins.graphite.events.codecs

import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.MessageToByteEncoder
import io.qalipsis.api.events.Event
import io.qalipsis.api.logging.LoggerHelper.logger
import java.nio.ByteBuffer

/**
 * Implementation of [MessageToByteEncoder] for [graphite][https://github.com/graphite-project] pickle protocol.
 * Receives a list of [Event], encodes them to pickle format and then sends it to [ChannelHandlerContext] for further processing.
 *
 * @author rklymenko
 */
internal class GraphitePickleEncoder : MessageToByteEncoder<List<Event>>() {

    /**
     * Encodes incoming list of [Event] to [pickle][https://graphite.readthedocs.io/en/latest/feeding-carbon.html#the-pickle-protocol]
     * Sends encoded message to [ChannelHandlerContext]
     */
    override fun encode(
        ctx: ChannelHandlerContext,
        events: List<Event>, out: ByteBuf
    ) {
        out.retain()
        log.trace { "Encoding events: $events" }
        val message = generateEvent(events)
        out.writeBytes(message)
        ctx.writeAndFlush(out)
        log.trace { "Events flushed: $events" }
    }

    /**
     * Receives a list of [Event] and encodes it to [pickle][https://graphite.readthedocs.io/en/latest/feeding-carbon.html#the-pickle-protocol]
     * with payload header
     */
    private fun generateEvent(events: List<Event>): ByteArray {
        val payload = generatePayload(events)
        val payloadBytes = payload.encodeToByteArray()
        val header = ByteBuffer.allocate(4).putInt(payloadBytes.size).array()
        return header + payloadBytes
    }

    /**
     * Receives a list of [Event] and encodes it to [pickle][https://graphite.readthedocs.io/en/latest/feeding-carbon.html#the-pickle-protocol]
     */
    private fun generatePayload(events: List<Event>): String {
        val payload = StringBuilder()
        payload.append(MARK)
        payload.append(LIST)

        for (tuple in events) {
            payload.append(MARK)
            payload.append(STRING)
            payload.append(QUOTE)
            payload.append(tuple.name)
            for (tag in tuple.tags) {
                payload.append(SEMICOLON)
                payload.append(tag.key)
                payload.append(EQ)
                payload.append(tag.value)
            }
            payload.append(QUOTE)
            payload.append(LF)
            payload.append(MARK)
            payload.append(LONG)
            payload.append(tuple.timestamp.toEpochMilli() / 1000)
            payload.append(LONG)
            payload.append(LF)
            payload.append(STRING)
            payload.append(QUOTE)
            payload.append(tuple.value)
            payload.append(QUOTE)
            payload.append(LF)
            payload.append(TUPLE)
            payload.append(TUPLE)
            payload.append(APPEND)
        }

        payload.append(STOP)
        return payload.toString()
    }

    companion object {

        private val log = logger()

        private const val MARK = '('
        private const val STOP = '.'
        private const val LONG = 'L'
        private const val STRING = 'S'
        private const val APPEND = 'a'
        private const val LIST = 'l'
        private const val TUPLE = 't'
        private const val QUOTE = '\''
        private const val LF = '\n'
        private const val SEMICOLON = ";"
        private const val EQ = "="
    }
}