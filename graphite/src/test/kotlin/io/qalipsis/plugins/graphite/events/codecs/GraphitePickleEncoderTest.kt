package io.qalipsis.plugins.graphite.events.codecs

import io.aerisconsulting.catadioptre.invokeInvisible
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import io.mockk.confirmVerified
import io.mockk.impl.annotations.RelaxedMockK
import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandlerContext
import io.qalipsis.api.events.Event
import io.qalipsis.api.events.EventLevel
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.coVerifyOnce
import org.junit.jupiter.api.Test
import java.time.Instant
import org.junit.Assert
import java.nio.ByteBuffer

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
        val actualMessage = graphitePickleEncoder.invokeInvisible<String>("generatePayload", events)

        //then
        Assert.assertEquals(expectedMessage, actualMessage)
    }

    @Test
    fun `should generate payload for multiple events`() {
        //given
        val graphitePickleEncoder = GraphitePickleEncoder()
        val events = listOf(Event("boo", EventLevel.DEBUG, timestamp = Instant.ofEpochMilli(0L)), Event("boo2", EventLevel.DEBUG, timestamp = Instant.ofEpochMilli(0L)))
        val expectedMessage = "(l(S'boo'\n" +
                "(L0L\n" +
                "S'null'\n" +
                "tta(S'boo2'\n" +
                "(L0L\n" +
                "S'null'\n" +
                "tta."

        //when
        val actualMessage = graphitePickleEncoder.invokeInvisible<String>("generatePayload", events)

        //then
        Assert.assertEquals(expectedMessage, actualMessage)
    }

    @Test
    fun `should generate event bytes for single event`() {
        //given
        val graphitePickleEncoder = GraphitePickleEncoder()
        val events = listOf(Event("boo", EventLevel.DEBUG, timestamp = Instant.ofEpochMilli(0L)))
        val payload = graphitePickleEncoder.invokeInvisible<String>("generatePayload", events).toByteArray()
        val header = ByteBuffer.allocate(4).putInt(payload.size).array()
        val expectedEvent = header + payload

        //when
        val actualEvent = graphitePickleEncoder.invokeInvisible<ByteArray>("generateEvent", events)

        //then
        Assert.assertArrayEquals(expectedEvent, actualEvent)
    }

    @Test
    fun `should generate event bytes for multiple events`() {
        //given
        val graphitePickleEncoder = GraphitePickleEncoder()
        val events = listOf(Event("boo", EventLevel.DEBUG, timestamp = Instant.ofEpochMilli(0L)), Event("boo2", EventLevel.DEBUG, timestamp = Instant.ofEpochMilli(0L)))
        val payload = graphitePickleEncoder.invokeInvisible<String>("generatePayload", events).toByteArray()
        val header = ByteBuffer.allocate(4).putInt(payload.size).array()
        val expectedEvent = header + payload

        //when
        val actualEvent = graphitePickleEncoder.invokeInvisible<ByteArray>("generateEvent", events)

        //then
        Assert.assertArrayEquals(expectedEvent, actualEvent)
    }

    @Test
    fun `should encode messages from context`() {
        //given
        val graphitePickleEncoder = GraphitePickleEncoder()
        val events = listOf(Event("boo", EventLevel.DEBUG, timestamp = Instant.ofEpochMilli(0L)), Event("boo2", EventLevel.DEBUG, timestamp = Instant.ofEpochMilli(0L)))
        val payload = graphitePickleEncoder.invokeInvisible<String>("generatePayload", events).toByteArray()
        val header = ByteBuffer.allocate(4).putInt(payload.size).array()
        val expectedEvents = header + payload

        //when
        graphitePickleEncoder.invokeInvisible<Unit>("encode", ctx, events, out)

        //then
        coVerifyOnce {
            out.writeBytes(expectedEvents)
            ctx.writeAndFlush(out)
            out.retain()
        }
        confirmVerified(out, ctx)
    }
}