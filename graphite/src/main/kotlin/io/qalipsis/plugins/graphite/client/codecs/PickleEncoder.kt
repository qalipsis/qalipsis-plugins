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

package io.qalipsis.plugins.graphite.client.codecs


import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelHandler.Sharable
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.MessageToByteEncoder
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.plugins.graphite.client.GraphiteRecord
import java.util.regex.Pattern

/**
 * Implementation of [MessageToByteEncoder] for [graphite][https://github.com/graphite-project] pickle protocol,
 * supporting the emission of [GraphiteRecord]s.
 *
 * @author Eric Jessé
 */
@Sharable
internal class PickleEncoder : MessageToByteEncoder<List<GraphiteRecord>>() {

    public override fun encode(ctx: ChannelHandlerContext, data: List<GraphiteRecord>, out: ByteBuf) {
        out.retain()
        log.trace { "Encoding data: $data" }

        val payload = Unpooled.buffer()
        payload.writeBytes(MARK)
        payload.writeBytes(LIST_MARKER)
        data.forEach { convert(it, payload) }
        payload.writeBytes(STOP)

        val header = Unpooled.buffer(4)
        header.writeInt(payload.writerIndex())

        out.writeBytes(header)
        out.writeBytes(payload)

        ctx.writeAndFlush(out).addListener {
            if (!it.isSuccess) {
                log.debug(it.cause()) { " Failed to send $data with the pickle encoder" }
            } else {
                log.trace { "Data flushed: $data" }
            }
        }
    }

    /**
     * Encodes a [GraphiteRecord] to [pickle][https://docs.python.org/3/library/pickle.html].
     */
    private fun convert(data: GraphiteRecord, out: ByteBuf) {
        // Starts the outer tuple.
        out.writeBytes(MARK)
        out.writeBytes(STRING_TYPE_MARKER)
        // The single quotes are to match python's repr("abcd").
        out.writeBytes(QUOTE)
        out.writeBytes(data.path.sanitize().encodeToByteArray())

        data.tags.forEach { (key, value) ->
            out.writeBytes(TAG_SEPARATOR)
            out.writeBytes(key.sanitize().encodeToByteArray())
            out.writeBytes(TAG_VALUE_SEPARATOR)
            out.writeBytes(value.sanitize().encodeToByteArray())
        }
        out.writeBytes(QUOTE)
        out.writeBytes(LF)

        // Starts the inner tuple.
        out.writeBytes(MARK)

        // The timestamp is long, but encoded as a string.
        out.writeBytes(LONG_TYPE_MARKER)
        out.writeBytes(data.timestamp.epochSecond.toString().encodeToByteArray())
        // The trailing L is to match python's repr(long(1234)).
        out.writeBytes(LONG_TYPE_MARKER)
        out.writeBytes(LF)

        out.writeBytes(STRING_TYPE_MARKER)
        // The single quotes are to match python's repr("abcd").
        out.writeBytes(QUOTE)
        out.writeBytes("${data.value}".encodeToByteArray())
        out.writeBytes(QUOTE)
        out.writeBytes(LF)

        // Closes the inner tuple.
        out.writeBytes(TUPLE)
        // Closes the outer tuple.
        out.writeBytes(TUPLE)

        out.writeBytes(APPEND)
    }

    /**
     * Replace spaces to a hyphen(-) as well as convert to a lowercase. This is to provide uniformity among name indexes.
     */
    private fun String.sanitize() = WHITESPACE.matcher(this.trim()).replaceAll(SUBSTITUTE_FOR_WHITESPACE).lowercase()

    companion object {

        private val MARK = "(".encodeToByteArray()

        private val STOP = ".".encodeToByteArray()

        private val LONG_TYPE_MARKER = "L".encodeToByteArray()

        private val STRING_TYPE_MARKER = "S".encodeToByteArray()

        private val APPEND = "a".encodeToByteArray()

        private val LIST_MARKER = "l".encodeToByteArray()

        private val TUPLE = "t".encodeToByteArray()

        private val QUOTE = "\"".encodeToByteArray()

        private val LF = "\n".encodeToByteArray()

        private val TAG_VALUE_SEPARATOR = "=".encodeToByteArray()

        private val TAG_SEPARATOR = ";".encodeToByteArray()

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