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

package io.qalipsis.plugins.redis.lettuce.streams.consumer

import assertk.all
import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.index
import assertk.assertions.isEqualTo
import assertk.assertions.isLessThanOrEqualTo
import assertk.assertions.prop
import io.lettuce.core.StreamMessage
import io.mockk.coEvery
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.spyk
import io.mockk.verify
import io.qalipsis.api.context.StepOutput
import io.qalipsis.api.context.StepStartStopContext
import io.qalipsis.api.meters.CampaignMeterRegistry
import io.qalipsis.api.meters.Counter
import io.qalipsis.api.meters.Meter
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.relaxedMockk
import kotlinx.coroutines.channels.Channel
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.Instant
import java.util.concurrent.atomic.AtomicLong

@WithMockk
internal class LettuceStreamsConsumerBatchConverterTest {

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
        val tags: Map<String, String> = emptyMap()
        val meterRegistry = relaxedMockk<CampaignMeterRegistry> {
            every { counter("scenario-name", "step-name", "redis-lettuce-streams-consumer-records", refEq(tags)) } returns counter
            every { counter.report(any()) } returns counter
            every { counter("scenario-name", "step-name", "redis-lettuce-streams-consumer-records-bytes", refEq(tags)) } returns byteCounter
            every { byteCounter.report(any()) } returns byteCounter
        }

        val startStopContext = relaxedMockk<StepStartStopContext> {
            every { toMetersTags() } returns tags
            every { scenarioName } returns "scenario-name"
            every { stepName } returns "step-name"
        }

        val converter = spyk(
            LettuceStreamsConsumerBatchConverter(
                meterRegistry
            )
        )
        converter.start(startStopContext)

        val records = listOf(
            StreamMessage("stream".toByteArray(), "id", mapOf("key".toByteArray() to "value".toByteArray())),
            StreamMessage("stream".toByteArray(), "id2", mapOf("key2".toByteArray() to "value2".toByteArray()))
        )

        //when
        val channel = Channel<LettuceStreamsConsumerResult>(capacity = 2)
        val output = relaxedMockk<StepOutput<LettuceStreamsConsumerResult>> {
            coEvery { send(any()) } coAnswers {
                channel.send(firstArg())
            }
        }
        converter.supply(AtomicLong(0), records, output)
        val results = channel.receive()


        //then
        verify(exactly = 2) { counter.increment() }
        verify(exactly = 2) { byteCounter.increment(any()) }
        verify(exactly = 1) { counter.report(any<Meter.ReportingConfiguration<Counter>.() -> Unit>())}
        verify(exactly = 1) { byteCounter.report(any<Meter.ReportingConfiguration<Counter>.() -> Unit>())}

        assertThat(results.records).all {
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