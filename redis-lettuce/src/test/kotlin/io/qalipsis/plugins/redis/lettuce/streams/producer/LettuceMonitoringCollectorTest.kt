package io.qalipsis.plugins.redis.lettuce.streams.producer

import assertk.all
import assertk.assertThat
import assertk.assertions.isEqualTo
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.spyk
import io.mockk.verify
import io.qalipsis.api.events.EventsLogger
import io.qalipsis.plugins.redis.lettuce.LettuceMonitoringCollector
import io.qalipsis.plugins.redis.lettuce.MetersImpl
import io.qalipsis.test.assertk.prop
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.relaxedMockk
import io.qalipsis.test.steps.StepTestHelper
import io.qalipsis.test.steps.TestStepContext
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Duration

@WithMockk
internal class LettuceMonitoringCollectorTest {
    @RelaxedMockK
    private lateinit var eventsLogger: EventsLogger

    @RelaxedMockK
    private lateinit var meterRegistry: MeterRegistry

    private lateinit var stepContext: TestStepContext<String, LettuceStreamsProducerResult<String>>

    @BeforeEach
    fun setUp() {
        stepContext = StepTestHelper.createStepContext(input = "Any")

    }

    @Test
    fun `should record sending data`() {
        val counter = relaxedMockk<Counter> { }
        every { meterRegistry.counter(any(), refEq(stepContext.toMetersTags())) } returns counter
        val monitoringCollector =
            spyk(LettuceMonitoringCollector(stepContext, eventsLogger, meterRegistry, "streams"))

        monitoringCollector.recordSendingData(10)

        val metersResult = MetersImpl(bytesToBeSent = 10, sentBytes = 0)
        val result = monitoringCollector.toResult("Any")

        verify {
            meterRegistry.counter(eq("lettuce-streams-sending-bytes"), refEq(stepContext.toMetersTags()))
            counter.increment(eq(10.toDouble()))
            eventsLogger.info(name = eq("lettuce.streams.sending.bytes"), value = eq(10), timestamp = any(), tags = any<Map<String, String>>())
        }

        assertThat(result).all {
            prop("input").isEqualTo("Any")
            prop("sendingFailures").isEqualTo(mutableListOf<Throwable>())
            prop("meters").isEqualTo(metersResult)
        }

        confirmVerified(eventsLogger, meterRegistry, counter)
    }

    @Test
    fun `should record sending data with more than 1 record`() {
        val counter = relaxedMockk<Counter> { }
        every { meterRegistry.counter(any(), refEq(stepContext.toMetersTags())) } returns counter
        val monitoringCollector =
            spyk(LettuceMonitoringCollector(stepContext, eventsLogger, meterRegistry, "streams"))

        monitoringCollector.recordSendingData(10)
        monitoringCollector.recordSendingData(10)

        val metersResult = MetersImpl(bytesToBeSent = 20, sentBytes = 0)
        val result = monitoringCollector.toResult("Any")

        verify(exactly = 2) {
            meterRegistry.counter(eq("lettuce-streams-sending-bytes"), refEq(stepContext.toMetersTags()))
            counter.increment(eq(10.toDouble()))
            eventsLogger.info(name = eq("lettuce.streams.sending.bytes"), value = eq(10), timestamp = any(), tags = any<Map<String, String>>())
        }

        assertThat(result).all {
            prop("input").isEqualTo("Any")
            prop("sendingFailures").isEqualTo(mutableListOf<Throwable>())
            prop("meters").isEqualTo(metersResult)
        }

        confirmVerified(eventsLogger, meterRegistry, counter)
    }


    @Test
    fun `should record sent data success`() {
        val counter = relaxedMockk<Counter> { }
        every { meterRegistry.counter(any(), refEq(stepContext.toMetersTags())) } returns counter
        val monitoringCollector =
            spyk(LettuceMonitoringCollector(stepContext, eventsLogger, meterRegistry, "streams"))

        monitoringCollector.recordSentDataSuccess(Duration.ofSeconds(1), 10)
        monitoringCollector.recordSentDataSuccess(Duration.ofSeconds(1), 10)

        val metersResult = MetersImpl(bytesToBeSent = 0, sentBytes = 20)
        val result = monitoringCollector.toResult("Any")

        verify(exactly = 2) {
            meterRegistry.counter(eq("lettuce-streams-sent-bytes"), refEq(stepContext.toMetersTags()))
            counter.increment(eq(10.toDouble()))
            eventsLogger.info(name = eq("lettuce.streams.sent.bytes"), value = eq(arrayOf(Duration.ofSeconds(1), 10)), timestamp = any(), tags = any<Map<String, String>>())
        }

        assertThat(result).all {
            prop("input").isEqualTo("Any")
            prop("sendingFailures").isEqualTo(mutableListOf<Throwable>())
            prop("meters").isEqualTo(metersResult)
        }

        confirmVerified(eventsLogger, meterRegistry, counter)
    }

    @Test
    fun `should record sent data failure`() {
        val counter = relaxedMockk<Counter> { }
        every { meterRegistry.counter(any(), refEq(stepContext.toMetersTags())) } returns counter
        val monitoringCollector =
            spyk(LettuceMonitoringCollector(stepContext, eventsLogger, meterRegistry, "streams"))

        val exception1 = RuntimeException("Test throwable1")
        val exception2 = RuntimeException("Test throwable2")
        monitoringCollector.recordSentDataFailure(Duration.ofSeconds(1), exception1)
        monitoringCollector.recordSentDataFailure(Duration.ofSeconds(1), exception2)

        val metersResult = MetersImpl(bytesToBeSent = 0, sentBytes = 0)
        val result = monitoringCollector.toResult("Any")

        verify(exactly = 2) {
            meterRegistry.counter(eq("lettuce-streams-sending-failure"), refEq(stepContext.toMetersTags()))
            counter.increment()
            eventsLogger.warn(name = eq("lettuce.streams.sending.failed"), value = any(), timestamp = any(), tags = any<Map<String, String>>())
        }

        val failures = mutableListOf<Throwable>()
        failures.add(exception1)
        failures.add(exception2)

        assertThat(result).all {
            prop("input").isEqualTo("Any")
            prop("sendingFailures").isEqualTo(failures)
            prop("meters").isEqualTo(metersResult)
        }

        confirmVerified(eventsLogger, meterRegistry, counter)
    }
}