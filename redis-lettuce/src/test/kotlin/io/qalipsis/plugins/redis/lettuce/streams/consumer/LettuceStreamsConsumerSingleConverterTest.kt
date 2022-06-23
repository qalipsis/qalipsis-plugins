package io.qalipsis.plugins.redis.lettuce.streams.consumer

import assertk.all
import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.index
import assertk.assertions.isEqualTo
import assertk.assertions.isLessThanOrEqualTo
import assertk.assertions.prop
import io.lettuce.core.StreamMessage
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tags
import io.mockk.coEvery
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.spyk
import io.mockk.verify
import io.qalipsis.api.context.StepOutput
import io.qalipsis.api.context.StepStartStopContext
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.relaxedMockk
import kotlinx.coroutines.channels.Channel
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.Instant
import java.util.concurrent.atomic.AtomicLong

@WithMockk
internal class LettuceStreamsConsumerSingleConverterTest {

    @JvmField
    @RegisterExtension
    val testDispatcherProvider = TestDispatcherProvider()

    @RelaxedMockK
    private lateinit var counter: Counter

    @RelaxedMockK
    private lateinit var byteCounter: Counter

    @Test
    internal fun `should deserialize and count the records`() = testDispatcherProvider.runTest {
        //given
        val metersTags = relaxedMockk<Tags>()
        val meterRegistry = relaxedMockk<MeterRegistry> {
            every { counter("redis-lettuce-streams-consumer-records", refEq(metersTags)) } returns counter
            every { counter("redis-lettuce-streams-consumer-records-bytes", refEq(metersTags)) } returns byteCounter
        }

        val startStopContext = relaxedMockk<StepStartStopContext> {
            every { toMetersTags() } returns metersTags
        }

        val converter = spyk(
            LettuceStreamsConsumerSingleConverter(
                meterRegistry
            )
        )
        converter.start(startStopContext)

        val records = listOf(
            StreamMessage("stream".toByteArray(), "id", mapOf("key".toByteArray() to "value".toByteArray())),
            StreamMessage("stream".toByteArray(), "id2", mapOf("key2".toByteArray() to "value2".toByteArray()))

        )

        //when
        val channel = Channel<LettuceStreamsConsumedRecord>(capacity = 2)
        val output = relaxedMockk<StepOutput<LettuceStreamsConsumedRecord>> {
            coEvery { send(any()) } coAnswers {
                channel.send(firstArg())
            }
        }
        converter.supply(AtomicLong(0), records, output)

        val results = listOf(
            channel.receive(),
            channel.receive()
        )


        //then
        verify(exactly = 2) { counter.increment() }
        verify(exactly = 2) { byteCounter.increment(any()) }

        assertThat(results).all {
            hasSize(2)
            index(0).all {
                prop(LettuceStreamsConsumedRecord::offset).isEqualTo(0)
                prop(LettuceStreamsConsumedRecord::id).isEqualTo("id")
                prop(LettuceStreamsConsumedRecord::streamKey).isEqualTo("stream")
                prop(LettuceStreamsConsumedRecord::consumedTimestamp).isLessThanOrEqualTo(Instant.now().toEpochMilli())
                prop(LettuceStreamsConsumedRecord::value).isEqualTo(mapOf("key" to "value"))
            }
            index(1).all {
                prop(LettuceStreamsConsumedRecord::offset).isEqualTo(1)
                prop(LettuceStreamsConsumedRecord::id).isEqualTo("id2")
                prop(LettuceStreamsConsumedRecord::streamKey).isEqualTo("stream")
                prop(LettuceStreamsConsumedRecord::consumedTimestamp).isLessThanOrEqualTo(Instant.now().toEpochMilli())
                prop(LettuceStreamsConsumedRecord::value).isEqualTo(mapOf("key2" to "value2"))
            }
        }

        confirmVerified(counter, byteCounter)
    }
}