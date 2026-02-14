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

package io.qalipsis.plugins.http.monitoring

import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.justRun
import io.mockk.verify
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.events.EventsLogger
import io.qalipsis.api.meters.CampaignMeterRegistry
import io.qalipsis.api.meters.Counter
import io.qalipsis.api.meters.Throughput
import io.qalipsis.api.meters.Timer
import io.qalipsis.test.mockk.WithMockk
import java.time.Duration
import org.junit.jupiter.api.Test

@WithMockk
internal class HttpMonitoringCollectorTest {

    @MockK
    private lateinit var stepContext: StepContext<*, *>

    @MockK
    private lateinit var eventsLogger: EventsLogger

    @MockK
    private lateinit var meterRegistry: CampaignMeterRegistry

    @MockK
    private lateinit var counter: Counter

    @MockK
    private lateinit var timer: Timer

    @MockK
    private lateinit var throughput: Throughput

    private val eventTags = mapOf("scenario" to "test-scenario", "step" to "test-step")

    private val metersTags = mapOf("scenario" to "test-scenario", "step" to "test-step")

    private fun setupContext() {
        every { stepContext.toEventTags() } returns eventTags
        every { stepContext.toMetersTags() } returns metersTags
        every { stepContext.scenarioName } returns "test-scenario"
        every { stepContext.stepName } returns "test-step"

        justRun { eventsLogger.info(any(), any(), any(), any<Map<String, String>>()) }
        justRun { eventsLogger.debug(any(), any(), any(), any<Map<String, String>>()) }
        justRun { eventsLogger.warn(any(), any(), any(), any<Map<String, String>>()) }

        every { counter.report(any()) } returns counter
        justRun { counter.increment() }
        justRun { counter.increment(any()) }

        every { timer.report(any()) } returns timer
        justRun { timer.record(any<Duration>()) }

        every {
            meterRegistry.throughput(
                any(),
                any(),
                any(),
                any(),
                any(),
                any<Map<String, String>>()
            )
        } returns throughput
        every { throughput.report(any()) } returns throughput
        justRun { throughput.record() }
        justRun { throughput.record(any<Double>()) }
    }

    @Test
    fun `should record connecting event and meter`() {
        setupContext()
        every {
            meterRegistry.counter("test-scenario", "test-step", "http-apache-connecting", metersTags)
        } returns counter
        val collector = HttpMonitoringCollector(stepContext, eventsLogger, meterRegistry)

        collector.recordConnecting()

        verify { eventsLogger.info("http.apache.connecting", null, any(), eventTags) }
        verify { counter.increment() }
    }

    @Test
    fun `should record connected event and meter`() {
        setupContext()
        every {
            meterRegistry.timer("test-scenario", "test-step", "http-apache-connected", metersTags, any())
        } returns timer
        val collector = HttpMonitoringCollector(stepContext, eventsLogger, meterRegistry)
        val duration = Duration.ofMillis(42)

        collector.recordConnected(duration)

        verify { eventsLogger.info("http.apache.connected", duration, any(), eventTags) }
        verify { timer.record(duration) }
    }

    @Test
    fun `should record connection failure event and meter`() {
        setupContext()
        every {
            meterRegistry.timer("test-scenario", "test-step", "http-apache-connection-failure", metersTags, any())
        } returns timer
        val collector = HttpMonitoringCollector(stepContext, eventsLogger, meterRegistry)
        val duration = Duration.ofMillis(100)
        val exception = RuntimeException("Connection refused")

        collector.recordConnectionFailure(duration, exception)

        verify { eventsLogger.warn("http.apache.connection-failure", arrayOf(duration, exception), any(), eventTags) }
        verify { timer.record(duration) }
    }

    @Test
    fun `should record TLS connected event and meter`() {
        setupContext()
        every {
            meterRegistry.timer("test-scenario", "test-step", "http-apache-tls-connected", metersTags, any())
        } returns timer
        val collector = HttpMonitoringCollector(stepContext, eventsLogger, meterRegistry)
        val duration = Duration.ofMillis(55)

        collector.recordTlsConnected(duration)

        verify { eventsLogger.info("http.apache.tls-connected", duration, any(), eventTags) }
        verify { timer.record(duration) }
    }

    @Test
    fun `should record sent bytes event and meter when bytes are positive`() {
        setupContext()
        every {
            meterRegistry.counter("test-scenario", "test-step", "http-apache-sent-bytes", metersTags)
        } returns counter
        val collector = HttpMonitoringCollector(stepContext, eventsLogger, meterRegistry)

        collector.recordSentBytes(512L)

        verify { eventsLogger.debug("http.apache.sent-bytes", 512L, any(), eventTags) }
        verify { counter.increment(512.0) }
    }

    @Test
    fun `should not emit event for sent bytes when zero but still record meter`() {
        setupContext()
        every {
            meterRegistry.counter("test-scenario", "test-step", "http-apache-sent-bytes", metersTags)
        } returns counter
        val collector = HttpMonitoringCollector(stepContext, eventsLogger, meterRegistry)

        collector.recordSentBytes(0L)

        verify(exactly = 0) { eventsLogger.debug(any(), any<Long>(), any(), any<Map<String, String>>()) }
        verify { counter.increment(0.0) }
    }

    @Test
    fun `should record time to first byte event and meter`() {
        setupContext()
        every {
            meterRegistry.timer("test-scenario", "test-step", "http-apache-time-to-first-byte", metersTags, any())
        } returns timer
        val collector = HttpMonitoringCollector(stepContext, eventsLogger, meterRegistry)
        val duration = Duration.ofMillis(10)

        collector.recordTimeToFirstByte(duration)

        verify { eventsLogger.debug("http.apache.time-to-first-byte", duration, any(), eventTags) }
        verify { timer.record(duration) }
    }

    @Test
    fun `should record received response event and meter`() {
        setupContext()
        every {
            meterRegistry.timer("test-scenario", "test-step", "http-apache-received-response", metersTags, any())
        } returns timer
        val collector = HttpMonitoringCollector(stepContext, eventsLogger, meterRegistry)
        val duration = Duration.ofMillis(200)

        collector.recordReceivedResponse(duration, 1024L)

        verify { eventsLogger.info("http.apache.received-response", arrayOf(duration, 1024L), any(), eventTags) }
        verify { timer.record(duration) }
    }

    @Test
    fun `should record received bytes event and meter when bytes are positive`() {
        setupContext()
        every {
            meterRegistry.counter("test-scenario", "test-step", "http-apache-received-bytes", metersTags)
        } returns counter
        val collector = HttpMonitoringCollector(stepContext, eventsLogger, meterRegistry)

        collector.recordReceivedBytes(2048L)

        verify { eventsLogger.debug("http.apache.received-bytes", 2048L, any(), eventTags) }
        verify { counter.increment(2048.0) }
    }

    @Test
    fun `should record request failure event and meter`() {
        setupContext()
        every {
            meterRegistry.counter("test-scenario", "test-step", "http-apache-request-failure", metersTags)
        } returns counter
        val collector = HttpMonitoringCollector(stepContext, eventsLogger, meterRegistry)
        val duration = Duration.ofMillis(300)
        val exception = RuntimeException("Timeout")

        collector.recordRequestFailure(duration, exception)

        verify { eventsLogger.warn("http.apache.request-failure", arrayOf(duration, exception), any(), eventTags) }
        verify { counter.increment() }
    }

    @Test
    fun `should record HTTP status event and meter with status tag`() {
        setupContext()
        every {
            meterRegistry.counter(
                "test-scenario", "test-step", "http-apache-status",
                metersTags + ("status" to "200")
            )
        } returns counter
        val collector = HttpMonitoringCollector(stepContext, eventsLogger, meterRegistry)

        collector.recordHttpStatus(200)

        verify { eventsLogger.info("http.apache.status", 200, any(), eventTags) }
        verify { counter.increment() }
    }

    @Test
    fun `should not record anything when eventsLogger and meterRegistry are null`() {
        setupContext()
        val collector = HttpMonitoringCollector(stepContext, null, null)

        collector.recordConnecting()
        collector.recordConnected(Duration.ofMillis(42))
        collector.recordTlsConnected(Duration.ofMillis(55))
        collector.recordSentBytes(100L)
        collector.recordTimeToFirstByte(Duration.ofMillis(10))
        collector.recordReceivedResponse(Duration.ofMillis(200), 500L)
        collector.recordReceivedBytes(500L)
        collector.recordRequestFailure(Duration.ofMillis(300), RuntimeException())
        collector.recordHttpStatus(200)

        confirmVerified(eventsLogger, meterRegistry)
    }
}
