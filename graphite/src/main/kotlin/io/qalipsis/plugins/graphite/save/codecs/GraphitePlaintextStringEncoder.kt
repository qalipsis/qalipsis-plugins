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

import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.MessageToByteEncoder
import io.qalipsis.api.logging.LoggerHelper.logger

/**
 * Implementation of [MessageToByteEncoder] for [graphite][https://github.com/graphite-project] plaintext string protocol.
 * Receives a list of [String], encodes them to pickle format and then sends it to [ChannelHandlerContext] for further processing.
 *
 * @author Palina Bril
 */
internal class GraphitePlaintextStringEncoder : MessageToByteEncoder<List<String>>() {

    /**
     * Encodes incoming list of [String] to [plaintext][https://graphite.readthedocs.io/en/latest/feeding-carbon.html#the-plaintext-protocol]
     * Sends encoded messages one by one to [ChannelHandlerContext]
     */
    override fun encode(ctx: ChannelHandlerContext, messages: List<String>, out: ByteBuf) {
        out.retain()
        log.trace { "Encoding messages: $messages" }
        messages.forEach {
            out.writeBytes(it.encodeToByteArray())
        }
        ctx.writeAndFlush(out)
        log.trace { "Messages flushed: $messages" }
    }

    companion object {
        private val log = logger()
    }
}