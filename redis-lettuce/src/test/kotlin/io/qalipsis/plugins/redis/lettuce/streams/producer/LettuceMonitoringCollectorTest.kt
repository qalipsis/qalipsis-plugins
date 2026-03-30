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

package io.qalipsis.plugins.redis.lettuce.streams.producer

import assertk.all
import assertk.assertThat
import assertk.assertions.isEqualTo
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.spyk
import io.mockk.verify
import io.qalipsis.api.context.StepStartStopContext
import io.qalipsis.api.events.EventsLogger
import io.qalipsis.api.meters.Counter
import io.qalipsis.plugins.redis.lettuce.LettuceMonitoringCollector
import io.qalipsis.plugins.redis.lettuce.MetersImpl
import io.qalipsis.test.assertk.prop
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.steps.StepTestHelper
import io.qalipsis.test.steps.TestStepContext
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Duration

@WithMockk
internal class LettuceMonitoringCollectorTest {

    @RelaxedMockK
    private lateinit var metersTags: Map<String, String>

    @RelaxedMockK
    private lateinit var startStopContext: StepStartStopContext

    @RelaxedMockK
    private lateinit var sendingBytes: Counter

    @RelaxedMockK
    private lateinit var sentBytesMeter: Counter

    @RelaxedMockK
    private lateinit var sendingFailure: Counter

    @RelaxedMockK
    private lateinit var eventsLogger: EventsLogger

    private lateinit var stepContext: TestStepContext<String, LettuceStreamsProducerResult<String>>

    @BeforeEach
    fun setUp() {
        stepContext = StepTestHelper.createStepContext(input = "Any")
        every { startStopContext.toMetersTags() } returns metersTags
    }

    @Test
    fun `should record sending data`() {
        val monitoringCollector =
            spyk(
                LettuceMonitoringCollector(
                    stepContext,
                    eventsLogger,
                    sendingBytes,
                    sentBytesMeter,
                    sendingFailure,
                    "streams"
                ), recordPrivateCalls = true
            )

        monitoringCollector.recordSendingData(11)

        val metersResult = MetersImpl(bytesToBeSent = 11, sentBytes = 0)
        val result = monitoringCollector.toResult("Any")

        verify {
            sendingBytes.increment(11.0)
            eventsLogger.info(
                name = eq("redis.lettuce.streams.sending.bytes"),
                value = eq(11),
                timestamp = any(),
                tags = any<Map<String, String>>()
            )
        }

        assertThat(result).all {
            prop("input").isEqualTo("Any")
            prop("sendingFailures").isEqualTo(mutableListOf<Throwable>())
            prop("meters").isEqualTo(metersResult)
        }

        confirmVerified(eventsLogger, sendingBytes)
    }

    @Test
    fun `should record sending data with more than 1 record`() {
        val monitoringCollector =
            spyk(
                LettuceMonitoringCollector(
                    stepContext,
                    eventsLogger,
                    sendingBytes,
                    sentBytesMeter,
                    sendingFailure,
                    "streams"
                ), recordPrivateCalls = true
            )

        monitoringCollector.recordSendingData(10)
        monitoringCollector.recordSendingData(10)

        val metersResult = MetersImpl(bytesToBeSent = 20, sentBytes = 0)
        val result = monitoringCollector.toResult("Any")

        verify(exactly = 2) {
            sendingBytes.increment(10.0)
            eventsLogger.info(
                name = eq("redis.lettuce.streams.sending.bytes"),
                value = eq(10),
                timestamp = any(),
                tags = any<Map<String, String>>()
            )
        }

        assertThat(result).all {
            prop("input").isEqualTo("Any")
            prop("sendingFailures").isEqualTo(mutableListOf<Throwable>())
            prop("meters").isEqualTo(metersResult)
        }

        confirmVerified(eventsLogger, sendingBytes)
    }

    @Test
    fun `should record sent data success`() {
        val monitoringCollector =
            spyk(
                LettuceMonitoringCollector(
                    stepContext,
                    eventsLogger,
                    sendingBytes,
                    sentBytesMeter,
                    sendingFailure,
                    "streams"
                ), recordPrivateCalls = true
            )

        monitoringCollector.recordSentDataSuccess(Duration.ofSeconds(1), 10)
        monitoringCollector.recordSentDataSuccess(Duration.ofSeconds(1), 10)

        val metersResult = MetersImpl(bytesToBeSent = 0, sentBytes = 20)
        val result = monitoringCollector.toResult("Any")

        verify(exactly = 2) {
            sentBytesMeter.increment(10.0)
            eventsLogger.info(
                name = eq("redis.lettuce.streams.sent.bytes"),
                value = eq(arrayOf(Duration.ofSeconds(1), 10)),
                timestamp = any(),
                tags = any<Map<String, String>>()
            )
        }

        assertThat(result).all {
            prop("input").isEqualTo("Any")
            prop("sendingFailures").isEqualTo(mutableListOf<Throwable>())
            prop("meters").isEqualTo(metersResult)
        }

        confirmVerified(eventsLogger, sentBytesMeter)
    }

    @Test
    fun `should record sent data failure`() {
        val monitoringCollector =
            spyk(
                LettuceMonitoringCollector(
                    stepContext,
                    eventsLogger,
                    sendingBytes,
                    sentBytesMeter,
                    sendingFailure,
                    "streams"
                ), recordPrivateCalls = true
            )

        val exception1 = RuntimeException("Test throwable1")
        val exception2 = RuntimeException("Test throwable2")

        monitoringCollector.recordSentDataFailure(Duration.ofSeconds(1), exception1)
        monitoringCollector.recordSentDataFailure(Duration.ofSeconds(1), exception2)

        val metersResult = MetersImpl(bytesToBeSent = 0, sentBytes = 0)
        val result = monitoringCollector.toResult("Any")

        verify(exactly = 2) {
            sendingFailure.increment()
            eventsLogger.warn(
                name = eq("redis.lettuce.streams.sending.failed"),
                value = any(),
                timestamp = any(),
                tags = any<Map<String, String>>()
            )
        }

        val failures = mutableListOf<Throwable>()
        failures.add(exception1)
        failures.add(exception2)

        assertThat(result).all {
            prop("input").isEqualTo("Any")
            prop("sendingFailures").isEqualTo(failures)
            prop("meters").isEqualTo(metersResult)
        }

        confirmVerified(eventsLogger, sendingFailure)
    }
}