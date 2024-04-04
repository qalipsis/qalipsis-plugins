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

package io.qalipsis.plugins.graphite.client.codecs


import io.aerisconsulting.catadioptre.KTestable
import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandler.Sharable
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.MessageToByteEncoder
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.plugins.graphite.client.GraphiteRecord
import java.text.Normalizer
import java.util.regex.Pattern

/**
 * Implementation of [MessageToByteEncoder] for [graphite][https://github.com/graphite-project] plaintext protocol,
 * supporting the emission of [GraphiteRecord]s.
 *
 * @author Eric Jessé
 */
@Sharable
internal class PlaintextEncoder : MessageToByteEncoder<List<GraphiteRecord>>() {

    public override fun encode(ctx: ChannelHandlerContext, data: List<GraphiteRecord>, out: ByteBuf) {
        out.retain()
        log.trace { "Encoding data: $data" }
        data.forEach { convert(it, out) }
        ctx.writeAndFlush(out).addListener {
            if (!it.isSuccess) {
                log.debug(it.cause()) { " Failed to send $data with the plaintext encoder" }
            } else {
                log.trace { "Data flushed: $data" }
            }
        }
    }

    /**
     * Encodes a [GraphiteRecord] to [plaintext][https://graphite.readthedocs.io/en/latest/feeding-carbon.html#the-plaintext-protocol].
     */
    @KTestable
    private fun convert(data: GraphiteRecord, out: ByteBuf) {
        val sanitizedPath = data.path.sanitize()
        out.writeBytes(sanitizedPath.encodeToByteArray())

        data.tags.forEach { (key, value) ->
            out.writeBytes(TAG_SEPARATOR)
            out.writeBytes(key.sanitize().encodeToByteArray())
            out.writeBytes(EQ)
            out.writeBytes(value.sanitize().encodeToByteArray())
        }

        out.writeBytes(FIELD_SEPARATOR)
        out.writeBytes("${data.value}".encodeToByteArray())

        out.writeBytes(FIELD_SEPARATOR)
        out.writeBytes(data.timestamp.epochSecond.toString().encodeToByteArray())

        out.writeBytes(RECORD_SEPARATOR)
    }

    /**
     * Replace spaces to a hyphen(-) as well as convert to a lowercase. This is to provide uniformity among name indexes.
     */
    private fun String.sanitize() = Normalizer.normalize(
        WHITESPACE.matcher(this.trim()).replaceAll(SUBSTITUTE_FOR_WHITESPACE).lowercase(),
        Normalizer.Form.NFKD
    )

    companion object {

        /**
         * Separator of tags.
         */
        private val TAG_SEPARATOR = ";".encodeToByteArray()

        /**
         * Separator of key and value of the tags.
         */
        private val EQ = "=".encodeToByteArray()

        /**
         * Separator of fields.
         */
        private val FIELD_SEPARATOR = " ".encodeToByteArray()

        /**
         * Separator of records.
         */
        private val RECORD_SEPARATOR = "\n".encodeToByteArray()

        /**
         * Detectors of whitespace in names, keys, values....
         */
        private val WHITESPACE = Pattern.compile("\\s+")

        /**
         * Value to replace the whitespaces names, keys, values....
         */
        private const val SUBSTITUTE_FOR_WHITESPACE = "-"

        private val log = logger()
    }
}