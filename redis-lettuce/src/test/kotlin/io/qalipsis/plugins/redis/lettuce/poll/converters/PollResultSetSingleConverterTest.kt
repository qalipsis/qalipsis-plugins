package io.qalipsis.plugins.redis.lettuce.poll.converters

import assertk.all
import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.index
import assertk.assertions.isEqualTo
import assertk.assertions.isLessThanOrEqualTo
import assertk.assertions.prop
import io.lettuce.core.ScoredValue
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tags
import io.mockk.coJustRun
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.impl.annotations.SpyK
import io.qalipsis.api.context.StepStartStopContext
import io.qalipsis.api.events.EventsLogger
import io.qalipsis.plugins.redis.lettuce.RedisRecord
import io.qalipsis.plugins.redis.lettuce.poll.PollRawResult
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.relaxedMockk
import io.qalipsis.test.mockk.verifyOnce
import kotlinx.coroutines.test.runBlockingTest
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicLong

/**
 *
 * @author Gabriel Moraes
 */
@WithMockk
internal class PollResultSetSingleConverterTest {

    private val eventsLogger = relaxedMockk<EventsLogger>()

    private val recordsCounter = relaxedMockk<Counter>()

    private val recordsBytes = relaxedMockk<Counter>()

    @SpyK
    private var redisToJavaConverter = RedisToJavaConverter()

    @Test
    internal fun `should deserialize and count the records`() = runBlockingTest {
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
        val metersTags = relaxedMockk<Tags>()
        val meterRegistry = relaxedMockk<MeterRegistry> {
            every { counter("redis-lettuce-poll-method-records", refEq(metersTags)) } returns recordsCounter
            every { counter("redis-lettuce-poll-method-records-bytes", refEq(metersTags)) } returns recordsBytes
        }

        val startStopContext = relaxedMockk<StepStartStopContext> {
            every { toMetersTags() } returns metersTags
        }
        val tags: Map<String, String> = startStopContext.toEventTags()

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
            eventsLogger.info("redis.lettuce.poll.method.records-count", 2, any(), tags = tags)
            eventsLogger.info("redis.lettuce.poll.method.records-bytes", 9, any(), tags = tags)
        }
        confirmVerified(recordsCounter, recordsBytes, eventsLogger)
    }

    @Test
    internal fun `should deserialize scored value and count the records`() = runBlockingTest {
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
        val metersTags = relaxedMockk<Tags>()
        val meterRegistry = relaxedMockk<MeterRegistry> {
            every { counter("redis-lettuce-poll-method-records", refEq(metersTags)) } returns recordsCounter
            every { counter("redis-lettuce-poll-method-records-bytes", refEq(metersTags)) } returns recordsBytes
        }

        val startStopContext = relaxedMockk<StepStartStopContext> {
            every { toMetersTags() } returns metersTags
        }
        val tags: Map<String, String> = startStopContext.toEventTags()

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
            eventsLogger.info("redis.lettuce.poll.method.records-count", 2, any(), tags = tags)
            eventsLogger.info("redis.lettuce.poll.method.records-bytes", 25, any(), tags = tags)
        }
        confirmVerified(recordsCounter, recordsBytes, eventsLogger)
    }

    @Test
    internal fun `should deserialize map and count the records`() = runBlockingTest {
        //given
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
        val metersTags = relaxedMockk<Tags>()
        val meterRegistry = relaxedMockk<MeterRegistry> {
            every { counter("redis-lettuce-poll-method-records", refEq(metersTags)) } returns recordsCounter
            every { counter("redis-lettuce-poll-method-records-bytes", refEq(metersTags)) } returns recordsBytes
        }

        val startStopContext = relaxedMockk<StepStartStopContext> {
            every { toMetersTags() } returns metersTags
        }
        val tags: Map<String, String> = startStopContext.toEventTags()

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
            eventsLogger.info("redis.lettuce.poll.method.records-count", 2, any(), tags = tags)
            eventsLogger.info("redis.lettuce.poll.method.records-bytes", 21, any(), tags = tags)
        }
        confirmVerified(recordsCounter, recordsBytes, eventsLogger)
    }
}
