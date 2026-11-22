/*
 * QALIPSIS
 * Copyright (C) 2025 AERIS IT Solutions GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package io.qalipsis.plugins.netty.monitoring

import assertk.assertThat
import assertk.assertions.isFalse
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isTrue
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.events.EventsLogger
import io.qalipsis.api.meters.CampaignMeterRegistry
import io.qalipsis.api.meters.Counter
import io.qalipsis.api.meters.Throughput
import io.qalipsis.api.meters.Timer
import org.junit.jupiter.api.Test
import java.time.Duration

internal class StepContextBasedSocketMonitoringCollectorTest {

    private fun stepContext() = mockk<StepContext<*, *>>(relaxed = true) {
        every { toMetersTags() } returns mapOf("step" to "my-step")
    }

    @Test
    fun `given neither events nor meters when creating the collector then the monitoring is disabled and the event tags are not built`() {
        val stepContext = stepContext()
        val collector = StepContextBasedSocketMonitoringCollector(stepContext, null, null, "http")

        assertThat(collector.isMonitoringEnabled).isFalse()
        verify(exactly = 0) { stepContext.toEventTags() }
    }

    @Test
    fun `given events when creating the collector then the monitoring is enabled and the event tags are built`() {
        val stepContext = stepContext()
        val collector = StepContextBasedSocketMonitoringCollector(stepContext, mockk<EventsLogger>(), null, "http")

        assertThat(collector.isMonitoringEnabled).isTrue()
        verify { stepContext.toEventTags() }
    }

    @Test
    fun `given meters only when creating the collector then the monitoring is enabled`() {
        val stepContext = stepContext()
        val collector =
            StepContextBasedSocketMonitoringCollector(stepContext, null, mockk<CampaignMeterRegistry>(), "http")

        assertThat(collector.isMonitoringEnabled).isTrue()
        verify(exactly = 0) { stepContext.toEventTags() }
    }

    @Test
    fun `given nothing sent with the collector when a reception completes then no time is recorded`() {
        // given
        val timer = mockk<Timer>(relaxed = true) { every { report(any()) } returns this }
        val meterRegistry = meterRegistry(timer)
        val collector = StepContextBasedSocketMonitoringCollector(stepContext(), null, meterRegistry, "http")

        // when
        collector.recordReceivingData()
        collector.recordReceptionComplete()

        // then
        assertThat(collector.meters.timeToFirstByte).isNull()
        assertThat(collector.meters.timeToLastByte).isNull()
        verify(exactly = 0) { timer.record(any<Duration>()) }
    }

    @Test
    fun `given data sent with the collector when a reception completes then the time since the first sent byte is recorded`() {
        // given
        val timer = mockk<Timer>(relaxed = true) { every { report(any()) } returns this }
        val meterRegistry = meterRegistry(timer)
        val collector = StepContextBasedSocketMonitoringCollector(stepContext(), null, meterRegistry, "http")
        collector.recordSendingData(10)

        // when
        collector.recordReceptionComplete()

        // then
        assertThat(collector.meters.timeToLastByte).isNotNull()
        verify { timer.record(collector.meters.timeToLastByte!!) }
    }

    private fun meterRegistry(timerMeter: Timer): CampaignMeterRegistry {
        val counterMeter = mockk<Counter>(relaxed = true) { every { report(any()) } returns this }
        val throughputMeter = mockk<Throughput>(relaxed = true) { every { report(any()) } returns this }
        val registry = mockk<CampaignMeterRegistry>(relaxed = true)
        every {
            registry.timer(any<String>(), any<String>(), any<String>(), any<Map<String, String>>(), any<Collection<Double>>())
        } returns timerMeter
        every { registry.counter(any<String>(), any<String>(), any<String>(), any<Map<String, String>>()) } returns counterMeter
        every {
            registry.throughput(
                any<String>(), any<String>(), any<String>(), any(), any<Collection<Double>>(), any<Map<String, String>>()
            )
        } returns throughputMeter
        return registry
    }
}
