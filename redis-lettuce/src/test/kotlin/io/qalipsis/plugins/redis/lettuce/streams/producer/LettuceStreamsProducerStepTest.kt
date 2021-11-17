package io.qalipsis.plugins.redis.lettuce.streams.producer

import assertk.all
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import assertk.assertions.isNullOrEmpty
import assertk.assertions.isSameAs
import assertk.assertions.prop
import io.aerisconsulting.catadioptre.setProperty
import io.lettuce.core.api.StatefulRedisConnection
import io.micrometer.core.instrument.MeterRegistry
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.confirmVerified
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.slot
import io.mockk.spyk
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.context.StepId
import io.qalipsis.api.events.EventsLogger
import io.qalipsis.plugins.redis.lettuce.LettuceMonitoringCollector
import io.qalipsis.test.assertk.prop
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.relaxedMockk
import io.qalipsis.test.steps.StepTestHelper
import kotlinx.coroutines.channels.Channel
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

@WithMockk
internal class LettuceStreamsProducerStepTest {

    @JvmField
    @RegisterExtension
    val testDispatcherProvider = TestDispatcherProvider()

    private var recordsFactory: (suspend (ctx: StepContext<*, *>, input: String) -> List<LettuceStreamsProduceRecord>) =
        relaxedMockk { }

    @RelaxedMockK
    private lateinit var eventsLogger: EventsLogger

    @RelaxedMockK
    private lateinit var meterRegistry: MeterRegistry

    @Test
    fun `should publish without recording metrics`() = testDispatcherProvider.runTest {
        coEvery { recordsFactory.invoke(any(), any()) } returns listOf(
            LettuceStreamsProduceRecord(
                "payload",
                mapOf("test" to "test")
            )
        )

        val lettuceStreamsProducerStep = spyk(
            LettuceStreamsProducerStep(
                StepId(), null, this.coroutineContext, relaxedMockk { },
                recordsFactory, null, null
            ), recordPrivateCalls = true
        )
        lettuceStreamsProducerStep.setProperty(
            "connection",
            relaxedMockk<StatefulRedisConnection<ByteArray, ByteArray>> { })

        val context = StepTestHelper.createStepContext<String, LettuceStreamsProducerResult<String>>(input = "Any")
        val monitoringCollector = slot<LettuceMonitoringCollector>()

        val producerResult = LettuceStreamsProducerResult(
            "Any",
            emptyList(),
            relaxedMockk()
        )

        coEvery {
            lettuceStreamsProducerStep["execute"](
                capture(monitoringCollector), eq("Any"), any<List<LettuceStreamsProduceRecord>>()
            )
        } returns producerResult

        lettuceStreamsProducerStep.execute(context)

        val result = (context.output as Channel<LettuceStreamsProducerResult<String>>).receive()
        assertThat(result).all {
            prop(LettuceStreamsProducerResult<String>::input).isEqualTo("Any")
            prop(LettuceStreamsProducerResult<String>::sendingFailures).isNullOrEmpty()
            prop(LettuceStreamsProducerResult<String>::meters).isSameAs(producerResult.meters)
        }

        coVerify {
            lettuceStreamsProducerStep["execute"](any<LettuceMonitoringCollector>(), eq("Any"), any<List<LettuceStreamsProduceRecord>>())
            lettuceStreamsProducerStep.execute(refEq(context))
        }
        assertThat(monitoringCollector.captured).all {
            prop("stepContext").isSameAs(context)
            prop("eventsLogger").isNull()
        }

        confirmVerified(lettuceStreamsProducerStep)
    }

    @Test
    fun `should publish recording metrics`() = testDispatcherProvider.runTest {
        val records = listOf(
            LettuceStreamsProduceRecord(
                "payload",
                mapOf("test" to "test")
            )
        )
        coEvery { recordsFactory.invoke(any(), any()) } returns records


        val lettuceStreamsProducerStep = spyk(
            LettuceStreamsProducerStep(
                StepId(), null, this.coroutineContext, relaxedMockk { },
                recordsFactory, meterRegistry, eventsLogger
            ), recordPrivateCalls = true
        )

        lettuceStreamsProducerStep.setProperty(
            "connection",
            relaxedMockk<StatefulRedisConnection<ByteArray, ByteArray>> { })

        val context = StepTestHelper.createStepContext<String, LettuceStreamsProducerResult<String>>(input = "Any")
        val monitoringCollector = slot<LettuceMonitoringCollector>()

        val producerResult = LettuceStreamsProducerResult(
            "Any",
            emptyList(),
            relaxedMockk()
        )

        coEvery {
            lettuceStreamsProducerStep["execute"](
                capture(monitoringCollector), eq("Any"), refEq(records)
            )
        } returns producerResult

        lettuceStreamsProducerStep.execute(context)

        val result = (context.output as Channel<LettuceStreamsProducerResult<String>>).receive()
        assertThat(result).all {
            prop(LettuceStreamsProducerResult<String>::input).isEqualTo("Any")
            prop(LettuceStreamsProducerResult<String>::sendingFailures).isNullOrEmpty()
            prop(LettuceStreamsProducerResult<String>::meters).isSameAs(producerResult.meters)
        }

        coVerify {
            lettuceStreamsProducerStep["execute"](any<LettuceMonitoringCollector>(), eq("Any"), refEq(records))
            lettuceStreamsProducerStep.execute(refEq(context))
        }
        assertThat(monitoringCollector.captured).all {
            prop("stepContext").isSameAs(context)
            prop("eventsLogger").isSameAs(eventsLogger)
        }

        confirmVerified(lettuceStreamsProducerStep)
    }
}