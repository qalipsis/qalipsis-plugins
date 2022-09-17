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
import io.qalipsis.api.events.EventTag
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.coVerifyOnce
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.time.Instant

/**
 * @author rklymenko
 */
@MicronautTest
@WithMockk
internal class GraphitePlaintextEncoderTest {

    @RelaxedMockK
    private lateinit var ctx: ChannelHandlerContext

    @RelaxedMockK
    private lateinit var out: ByteBuf

    @Test
    fun `should generate payload for single event`() {
        //given
        val graphitePickleEncoder = GraphitePlaintextEncoder()
        val event = Event("boo", EventLevel.DEBUG, timestamp = Instant.ofEpochMilli(0L), value = "booval")
        val expectedMessage = "boo booval 0\n"

        //when
        val actualMessage = graphitePickleEncoder.invokeInvisible<String>("generatePayload", event)

        //then
        Assertions.assertEquals(expectedMessage, actualMessage)
    }

    @Test
    fun `should generate payload with tags for single event`() {
        //given
        val graphitePickleEncoder = GraphitePlaintextEncoder()
        val event = Event("boo", EventLevel.DEBUG, timestamp = Instant.ofEpochMilli(0L), value = "booval", tags = listOf(
            EventTag("foo1", "bar1"), EventTag("foo2", "bar2")
        ))
        val expectedMessage = "boo;foo1=bar1;foo2=bar2 booval 0\n"

        //when
        val actualMessage = graphitePickleEncoder.invokeInvisible<String>("generatePayload", event)

        //then
        Assertions.assertEquals(expectedMessage, actualMessage)
    }

    @Test
    fun `should encode messages from context`() {
        //given
        val graphitePickleEncoder = GraphitePlaintextEncoder()
        val events = listOf(Event("boo", EventLevel.DEBUG, timestamp = Instant.ofEpochMilli(0L), value = "booval"))
        val expectedMessage = "boo booval 0\n"

        //when
        graphitePickleEncoder.invokeInvisible<Unit>("encode", ctx, events, out)

        //then
        coVerifyOnce {
            out.writeBytes(expectedMessage.toByteArray())
            ctx.writeAndFlush(out)
            out.retain()
        }
        confirmVerified(out, ctx)
    }
}