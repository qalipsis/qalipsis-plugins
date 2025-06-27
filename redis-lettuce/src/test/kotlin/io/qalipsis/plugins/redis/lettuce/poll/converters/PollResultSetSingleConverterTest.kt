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

package io.qalipsis.plugins.redis.lettuce.poll.converters

import assertk.all
import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.index
import assertk.assertions.isEqualTo
import assertk.assertions.isLessThanOrEqualTo
import assertk.assertions.prop
import io.lettuce.core.ScoredValue
import io.mockk.coJustRun
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.impl.annotations.SpyK
import io.qalipsis.api.context.StepStartStopContext
import io.qalipsis.api.events.EventsLogger
import io.qalipsis.api.meters.CampaignMeterRegistry
import io.qalipsis.api.meters.Counter
import io.qalipsis.api.meters.Meter
import io.qalipsis.plugins.redis.lettuce.RedisRecord
import io.qalipsis.plugins.redis.lettuce.poll.PollRawResult
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.relaxedMockk
import io.qalipsis.test.mockk.verifyOnce
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicLong

/**
 *
 * @author Gabriel Moraes
 */
@WithMockk
internal class PollResultSetSingleConverterTest {

    @JvmField
    @RegisterExtension
    val testDispatcherProvider = TestDispatcherProvider()

    @RelaxedMockK
    private lateinit var eventsLogger: EventsLogger

    @RelaxedMockK
    private lateinit var recordsCounter: Counter

    @RelaxedMockK
    private lateinit var recordsBytes: Counter

    @SpyK
    private var redisToJavaConverter = RedisToJavaConverter()

    @Test
    internal fun `should deserialize and count the records`() = testDispatcherProvider.runTest {
        //given
        val records = PollRawResult(
            listOf(
                "test".toByteArray(),
                "test2".toByteArray(),
            ),
            2,
            Duration.ofNanos(3),
            1
        )
        val tags: Map<String, String> = emptyMap()
        val meterRegistry = relaxedMockk<CampaignMeterRegistry> {
            every { counter("scenario-name", "step-name", "redis-lettuce-poll-method-records", refEq(tags)) } returns recordsCounter
            every { recordsCounter.report(any()) } returns recordsCounter
            every { counter("scenario-name", "step-name", "redis-lettuce-poll-method-records-bytes", refEq(tags)) } returns recordsBytes
            every { recordsBytes.report(any()) } returns recordsBytes
        }

        val startStopContext = relaxedMockk<StepStartStopContext> {
            every { toMetersTags() } returns tags
            every { scenarioName } returns "scenario-name"
            every { stepName } returns "step-name"
        }

        val converter = PollResultSetSingleConverter(
            redisToJavaConverter,
            eventsLogger = eventsLogger,
            meterRegistry = meterRegistry,
            "method"
        )
        //when
        val resultsCaptor = mutableListOf<RedisRecord<Any?>>()
        converter.start(startStopContext)
        converter.supply(
            AtomicLong(0),
            records,
            relaxedMockk { coJustRun { send(capture(resultsCaptor)) } }
        )

        //then
        assertThat(resultsCaptor).all {
            hasSize(2)
            index(0).all {
                prop(RedisRecord<*>::recordOffset).isEqualTo(0)
                prop(RedisRecord<*>::recordTimestamp).isLessThanOrEqualTo(Instant.now().toEpochMilli())
                prop(RedisRecord<*>::value).isEqualTo("test")
            }
            index(1).all {
                prop(RedisRecord<*>::recordOffset).isEqualTo(1)
                prop(RedisRecord<*>::recordTimestamp).isLessThanOrEqualTo(Instant.now().toEpochMilli())
                prop(RedisRecord<*>::value).isEqualTo("test2")
            }
        }
        verifyOnce {
            recordsCounter.increment(2.0)
            recordsBytes.increment(9.0)
            recordsCounter.report(any<Meter.ReportingConfiguration<Counter>.() -> Unit>())
            recordsBytes.report(any<Meter.ReportingConfiguration<Counter>.() -> Unit>())
            eventsLogger.info("redis.lettuce.poll.method.records-count", 2, any(), tags = tags)
            eventsLogger.info("redis.lettuce.poll.method.records-bytes", 9, any(), tags = tags)
        }
        confirmVerified(recordsCounter, recordsBytes, eventsLogger)
    }

    @Test
    internal fun `should deserialize scored value and count the records`() = testDispatcherProvider.runTest {
        //given
        val records = PollRawResult(
            listOf(
                ScoredValue.just(1.0, "test".toByteArray()),
                ScoredValue.just(5.0, "test2".toByteArray()),
            ),
            2,
            Duration.ofNanos(3),
            1
        )
        val tags: Map<String, String> = emptyMap()
        val meterRegistry = relaxedMockk<CampaignMeterRegistry> {
            every { counter("scenario-name", "step-name", "redis-lettuce-poll-method-records", refEq(tags)) } returns recordsCounter
            every { recordsCounter.report(any()) } returns recordsCounter
            every { counter("scenario-name", "step-name", "redis-lettuce-poll-method-records-bytes", refEq(tags)) } returns recordsBytes
            every { recordsBytes.report(any()) } returns recordsBytes
        }

        val startStopContext = relaxedMockk<StepStartStopContext> {
            every { toMetersTags() } returns tags
            every { scenarioName } returns "scenario-name"
            every { stepName } returns "step-name"
        }

        val converter = PollResultSetSingleConverter(
            redisToJavaConverter,
            eventsLogger = eventsLogger,
            meterRegistry = meterRegistry,
            "method"
        )

        //when
        val resultsCaptor = mutableListOf<RedisRecord<Any?>>()
        converter.start(startStopContext)
        converter.supply(
            AtomicLong(0),
            records,
            relaxedMockk { coJustRun { send(capture(resultsCaptor)) } }
        )

        //then
        assertThat(resultsCaptor).all {
            hasSize(2)
            index(0).all {
                prop(RedisRecord<*>::recordOffset).isEqualTo(0)
                prop(RedisRecord<*>::recordTimestamp).isLessThanOrEqualTo(Instant.now().toEpochMilli())
                prop(RedisRecord<*>::value).isEqualTo(1.0 to "test")
            }
            index(1).all {
                prop(RedisRecord<*>::recordOffset).isEqualTo(1)
                prop(RedisRecord<*>::recordTimestamp).isLessThanOrEqualTo(Instant.now().toEpochMilli())
                prop(RedisRecord<*>::value).isEqualTo(5.0 to "test2")
            }
        }
        verifyOnce {
            recordsCounter.increment(2.0)
            recordsBytes.increment(25.0)
            recordsCounter.report(any<Meter.ReportingConfiguration<Counter>.() -> Unit>())
            recordsBytes.report(any<Meter.ReportingConfiguration<Counter>.() -> Unit>())
            eventsLogger.info("redis.lettuce.poll.method.records-count", 2, any(), tags = tags)
            eventsLogger.info("redis.lettuce.poll.method.records-bytes", 25, any(), tags = tags)
        }
        confirmVerified(recordsCounter, recordsBytes, eventsLogger)
    }

    @Test
    internal fun `should deserialize map and count the records`() = testDispatcherProvider.runTest {
        //given
        val records = PollRawResult(
            mapOf(
                "test".toByteArray() to "value1".toByteArray(),
                "test2".toByteArray() to "value2".toByteArray(),
            ),
            2,
            Duration.ofNanos(3),
            1
        )
        val tags: Map<String, String> = emptyMap()
        val meterRegistry = relaxedMockk<CampaignMeterRegistry> {
            every { counter("scenario-name", "step-name", "redis-lettuce-poll-method-records", refEq(tags)) } returns recordsCounter
            every { recordsCounter.report(any()) } returns recordsCounter
            every { counter("scenario-name", "step-name", "redis-lettuce-poll-method-records-bytes", refEq(tags)) } returns recordsBytes
            every { recordsBytes.report(any()) } returns recordsBytes
        }

        val startStopContext = relaxedMockk<StepStartStopContext> {
            every { toMetersTags() } returns tags
            every { scenarioName } returns "scenario-name"
            every { stepName } returns "step-name"
        }

        val converter = PollResultSetSingleConverter(
            redisToJavaConverter,
            eventsLogger = eventsLogger,
            meterRegistry = meterRegistry,
            "method"
        )

        //when
        val resultsCaptor = mutableListOf<RedisRecord<Any?>>()
        converter.start(startStopContext)
        converter.supply(
            AtomicLong(0),
            records,
            relaxedMockk { coJustRun { send(capture(resultsCaptor)) } }
        )

        //then
        assertThat(resultsCaptor).all {
            hasSize(2)
            index(0).all {
                prop(RedisRecord<*>::recordOffset).isEqualTo(0)
                prop(RedisRecord<*>::recordTimestamp).isLessThanOrEqualTo(Instant.now().toEpochMilli())
                prop(RedisRecord<*>::value).isEqualTo("test" to "value1")
            }
            index(1).all {
                prop(RedisRecord<*>::recordOffset).isEqualTo(1)
                prop(RedisRecord<*>::recordTimestamp).isLessThanOrEqualTo(Instant.now().toEpochMilli())
                prop(RedisRecord<*>::value).isEqualTo("test2" to "value2")
            }
        }
        verifyOnce {
            recordsCounter.increment(2.0)
            recordsBytes.increment(21.0)
            recordsCounter.report(any<Meter.ReportingConfiguration<Counter>.() -> Unit>())
            recordsBytes.report(any<Meter.ReportingConfiguration<Counter>.() -> Unit>())
            eventsLogger.info("redis.lettuce.poll.method.records-count", 2, any(), tags = tags)
            eventsLogger.info("redis.lettuce.poll.method.records-bytes", 21, any(), tags = tags)
        }
        confirmVerified(recordsCounter, recordsBytes, eventsLogger)
    }
}
