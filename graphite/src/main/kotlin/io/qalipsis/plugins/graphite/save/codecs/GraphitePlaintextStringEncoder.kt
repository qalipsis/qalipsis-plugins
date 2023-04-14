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

package io.qalipsis.plugins.graphite.save.codecs

import io.aerisconsulting.catadioptre.KTestable
import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.MessageToByteEncoder
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.plugins.graphite.save.GraphiteRecord

/**
 * Implementation of [MessageToByteEncoder] for [graphite][https://github.com/graphite-project] plaintext string protocol.
 * Receives a list of [GraphiteRecord], encodes them to pickle format and then sends it to [ChannelHandlerContext] for further processing.
 * Converts Graphite records to plaintext.
 *
 * @author Palina Bril
 */
internal class GraphitePlaintextStringEncoder : MessageToByteEncoder<List<GraphiteRecord>>() {

    /**
     * Encodes incoming list of [String] to [plaintext][https://graphite.readthedocs.io/en/latest/feeding-carbon.html#the-plaintext-protocol]
     * Sends encoded messages one by one to [ChannelHandlerContext]
     * Converts Graphite records to plaintext.
     */
    override fun encode(ctx: ChannelHandlerContext, messages: List<GraphiteRecord>, out: ByteBuf) {
        out.retain()
        log.trace { "Encoding messages: $messages" }
        messages.forEach {
            out.writeBytes(convertToPlaintext(it).encodeToByteArray())
        }
        ctx.writeAndFlush(out)
        log.trace { "Messages flushed: $messages" }
    }

    @KTestable
    fun convertToPlaintext(record: GraphiteRecord): String {
        val payload = StringBuilder()
        payload.append(record.metricPath)
        payload.append(" ")
        payload.append(record.value)
        payload.append(" ")
        payload.append((record.timestamp?.toEpochMilli())?.div(1000))
        payload.append("\n")
        return payload.toString()
    }

    companion object {
        private val log = logger()
    }
}