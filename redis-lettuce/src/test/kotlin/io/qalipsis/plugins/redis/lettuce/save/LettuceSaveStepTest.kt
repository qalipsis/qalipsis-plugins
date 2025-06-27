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

package io.qalipsis.plugins.redis.lettuce.save

import assertk.all
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import assertk.assertions.isNullOrEmpty
import assertk.assertions.isSameAs
import assertk.assertions.prop
import io.aerisconsulting.catadioptre.setProperty
import io.lettuce.core.api.StatefulRedisConnection
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.confirmVerified
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.slot
import io.mockk.spyk
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.context.StepName
import io.qalipsis.api.events.EventsLogger
import io.qalipsis.api.meters.CampaignMeterRegistry
import io.qalipsis.plugins.redis.lettuce.LettuceMonitoringCollector
import io.qalipsis.plugins.redis.lettuce.save.records.HashRecord
import io.qalipsis.test.assertk.prop
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.relaxedMockk
import io.qalipsis.test.steps.StepTestHelper
import kotlinx.coroutines.channels.Channel
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

@WithMockk
internal class LettuceSaveStepTest {

    @JvmField
    @RegisterExtension
    val testDispatcherProvider = TestDispatcherProvider()

    private var recordsFactory: (suspend (ctx: StepContext<*, *>, input: String) -> List<LettuceSaveRecord<*>>) =
        relaxedMockk { }

    @RelaxedMockK
    private lateinit var eventsLogger: EventsLogger

    @RelaxedMockK
    private lateinit var meterRegistry: CampaignMeterRegistry


    @Test
    fun `should save without recording metrics`() = testDispatcherProvider.runTest {
        coEvery { recordsFactory.invoke(any(), any()) } returns listOf(
            HashRecord(
                "payload",
                mapOf("test" to "test")
            )
        )

        val lettuceSaveStep = spyk(
            LettuceSaveStep(
                StepName(), null, this.coroutineContext, relaxedMockk { },
                recordsFactory, null, null
            ), recordPrivateCalls = true
        )

        lettuceSaveStep.setProperty("connection", relaxedMockk<StatefulRedisConnection<ByteArray, ByteArray>> { })

        val context = StepTestHelper.createStepContext<String, LettuceSaveResult<String>>(input = "Any")
        val monitoringCollector = slot<LettuceMonitoringCollector>()

        val saveResult = LettuceSaveResult("Any", emptyList(), relaxedMockk())

        coEvery {
            lettuceSaveStep["execute"](
                capture(monitoringCollector), eq("Any"), any<List<LettuceSaveRecord<*>>>()
            )
        } returns saveResult

        lettuceSaveStep.execute(context)

        val result =
            (context.output as Channel<StepContext.StepOutputRecord<LettuceSaveResult<String>>>).receive().value
        assertThat(result).all {
            prop(LettuceSaveResult<String>::input).isEqualTo("Any")
            prop(LettuceSaveResult<String>::sendingFailures).isNullOrEmpty()
            prop(LettuceSaveResult<String>::meters).isSameAs(saveResult.meters)
        }

        coVerify {
            lettuceSaveStep["execute"](any<LettuceMonitoringCollector>(), eq("Any"), any<List<LettuceSaveRecord<*>>>())
            lettuceSaveStep.execute(refEq(context))
        }
        assertThat(monitoringCollector.captured).all {
            prop("stepContext").isSameAs(context)
            prop("eventsLogger").isNull()
        }

        confirmVerified(lettuceSaveStep)
    }

    @Test
    fun `should save recording metrics`() = testDispatcherProvider.runTest {
        val records = listOf(
            HashRecord(
                "payload",
                mapOf("test" to "test")
            )
        )
        coEvery { recordsFactory.invoke(any(), any()) } returns records


        val lettuceSaveStep = spyk(
            LettuceSaveStep(
                StepName(), null, this.coroutineContext, relaxedMockk { },
                recordsFactory, meterRegistry, eventsLogger
            ), recordPrivateCalls = true
        )

        lettuceSaveStep.setProperty(
            "connection",
            relaxedMockk<StatefulRedisConnection<ByteArray, ByteArray>> { })

        val context = StepTestHelper.createStepContext<String, LettuceSaveResult<String>>(input = "Any")
        val monitoringCollector = slot<LettuceMonitoringCollector>()

        val saveResult = LettuceSaveResult(
            "Any",
            emptyList(),
            relaxedMockk()
        )

        coEvery {
            lettuceSaveStep["execute"](
                capture(monitoringCollector), eq("Any"), refEq(records)
            )
        } returns saveResult

        lettuceSaveStep.execute(context)

        val result =
            (context.output as Channel<StepContext.StepOutputRecord<LettuceSaveResult<String>>>).receive().value
        assertThat(result).all {
            prop(LettuceSaveResult<String>::input).isEqualTo("Any")
            prop(LettuceSaveResult<String>::sendingFailures).isNullOrEmpty()
            prop(LettuceSaveResult<String>::meters).isSameAs(saveResult.meters)
        }

        coVerify {
            lettuceSaveStep["execute"](any<LettuceMonitoringCollector>(), eq("Any"), refEq(records))
            lettuceSaveStep.execute(refEq(context))
        }
        assertThat(monitoringCollector.captured).all {
            prop("stepContext").isSameAs(context)
            prop("eventsLogger").isSameAs(eventsLogger)
        }

        confirmVerified(lettuceSaveStep)
    }
}