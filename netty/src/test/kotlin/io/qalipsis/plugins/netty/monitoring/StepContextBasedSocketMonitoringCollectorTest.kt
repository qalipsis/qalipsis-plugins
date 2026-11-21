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
import assertk.assertions.isTrue
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.events.EventsLogger
import io.qalipsis.api.meters.CampaignMeterRegistry
import org.junit.jupiter.api.Test

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
}
