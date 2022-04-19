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

import io.aerisconsulting.catadioptre.invokeInvisible
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import io.mockk.confirmVerified
import io.mockk.impl.annotations.RelaxedMockK
import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandlerContext
import io.qalipsis.api.events.Event
import io.qalipsis.api.events.EventLevel
import io.qalipsis.plugins.graphite.poll.model.events.codecs.GraphitePickleEncoder
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.coVerifyOnce
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.nio.ByteBuffer
import java.time.Instant

/**
 * @author rklymenko
 */
@MicronautTest
@WithMockk
internal class GraphitePickleEncoderTest {

    @RelaxedMockK
    private lateinit var ctx: ChannelHandlerContext

    @RelaxedMockK
    private lateinit var out: ByteBuf

    @Test
    fun `should generate payload for single event`() {
        //given
        val graphitePickleEncoder = GraphitePickleEncoder()
        val events = listOf(Event("boo", EventLevel.DEBUG, timestamp = Instant.ofEpochMilli(0L)))
        val expectedMessage = "(l(S'boo'\n" +
                "(L0L\n" +
                "S'null'\n" +
                "tta."

        //when
        val actualMessage = graphitePickleEncoder.convertToPickle(events)

        //then
        Assertions.assertEquals(expectedMessage, actualMessage)
    }

    @Test
    fun `should generate payload for multiple events`() {
        //given
        val graphitePickleEncoder = GraphitePickleEncoder()
        val events = listOf(
            Event("boo", EventLevel.DEBUG, timestamp = Instant.ofEpochMilli(0L)),
            Event("boo2", EventLevel.DEBUG, timestamp = Instant.ofEpochMilli(0L))
        )
        val expectedMessage = "(l(S'boo'\n" +
                "(L0L\n" +
                "S'null'\n" +
                "tta(S'boo2'\n" +
                "(L0L\n" +
                "S'null'\n" +
                "tta."

        //when
        val actualMessage = graphitePickleEncoder.convertToPickle(events)

        //then
        Assertions.assertEquals(expectedMessage, actualMessage)
    }

    @Test
    fun `should generate event bytes for single event`() {
        //given
        val graphitePickleEncoder = GraphitePickleEncoder()
        val events = listOf(Event("boo", EventLevel.DEBUG, timestamp = Instant.ofEpochMilli(0L)))
        val payload = graphitePickleEncoder.convertToPickle(events).encodeToByteArray()
        val header = ByteBuffer.allocate(4).putInt(payload.size).array()
        val expectedEvent = header + payload

        //when
        val actualEvent = graphitePickleEncoder.invokeInvisible<ByteArray>("serializeEvents", events)

        //then
        Assertions.assertArrayEquals(expectedEvent, actualEvent)
    }

    @Test
    fun `should generate event bytes for multiple events`() {
        //given
        val graphitePickleEncoder = GraphitePickleEncoder()
        val events = listOf(
            Event("boo", EventLevel.DEBUG, timestamp = Instant.ofEpochMilli(0L)),
            Event("boo2", EventLevel.DEBUG, timestamp = Instant.ofEpochMilli(0L))
        )
        val payload = graphitePickleEncoder.convertToPickle(events).encodeToByteArray()
        val header = ByteBuffer.allocate(4).putInt(payload.size).array()
        val expectedEvent = header + payload

        //when
        val actualEvent = graphitePickleEncoder.invokeInvisible<ByteArray>("serializeEvents", events)

        //then
        Assertions.assertArrayEquals(expectedEvent, actualEvent)
    }

    @Test
    fun `should encode messages from context`() {
        //given
        val graphitePickleEncoder = GraphitePickleEncoder()
        val events = listOf(
            Event("boo", EventLevel.DEBUG, timestamp = Instant.ofEpochMilli(0L)),
            Event("boo2", EventLevel.DEBUG, timestamp = Instant.ofEpochMilli(0L))
        )
        val payload = graphitePickleEncoder.convertToPickle(events).encodeToByteArray()
        val header = ByteBuffer.allocate(4).putInt(payload.size).array()
        val expectedEvents = header + payload

        //when
        graphitePickleEncoder.encode(ctx, events, out)

        //then
        coVerifyOnce {
            out.writeBytes(expectedEvents)
            ctx.writeAndFlush(out)
            out.retain()
        }
        confirmVerified(out, ctx)
    }
}