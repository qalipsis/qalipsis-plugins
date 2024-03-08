/*
 * Copyright 2022 AERIS IT Solutions GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package io.qalipsis.plugins.rabbitmq.producer

import assertk.all
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isSameAs
import assertk.assertions.prop
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.mockk
import io.mockk.slot
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.context.StepStartStopContext
import io.qalipsis.api.events.EventsLogger
import io.qalipsis.api.meters.CampaignMeterRegistry
import io.qalipsis.api.meters.Counter
import io.qalipsis.api.retry.RetryPolicy
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.coVerifyNever
import io.qalipsis.test.mockk.coVerifyOnce
import io.qalipsis.test.mockk.relaxedMockk
import io.qalipsis.test.mockk.verifyOnce
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.RegisterExtension

/**
 * @author rklymenko
 */
@WithMockk
internal class RabbitMqProducerStepTest {

    @RegisterExtension
    val testDispatcherProvider = TestDispatcherProvider()

    @RelaxedMockK
    private lateinit var retryPolicy: RetryPolicy

    @RelaxedMockK
    private lateinit var rabbitMqProducer: RabbitMqProducer

    @RelaxedMockK
    private lateinit var eventsLogger: EventsLogger

    @RelaxedMockK
    private lateinit var context: StepContext<Any, RabbitMqProducerResult<Any>>

    private lateinit var rabbitMqProducerStep: RabbitMqProducerStep<Any>

    private val recordsCount = relaxedMockk<Counter>()

    private val recordsByteCount = relaxedMockk<Counter>()

    private val failureCounter = relaxedMockk<Counter>()

    private val successCounter = relaxedMockk<Counter>()

    private val successByteCounter = relaxedMockk<Counter>()

    private val data = listOf(
        RabbitMqProducerRecord(
            exchange = "test-exchange",
            routingKey = "test-routing-key",
            value = "test".toByteArray(),
            props = null
        )
    )

    @Test
    fun `should start and stop the step`() = testDispatcherProvider.runTest {
        // given
        val eventsTags: Map<String, String> = mockk()
        val metersTags: Map<String, String> = mockk()
        val stepStartStopContext = relaxedMockk<StepStartStopContext> {
            every { toEventTags() } returns eventsTags
            every { toMetersTags() } returns metersTags
            every { scenarioName } returns "test-scenario"
            every { stepName } returns "test-step"
        }
        val meterRegistry = relaxedMockk<CampaignMeterRegistry> {
            every {
                counter(
                    "test-scenario",
                    "test-step",
                    "rabbitmq-produce-records",
                    refEq(metersTags)
                )
            } returns recordsCount
            every { recordsCount.report(any()) } returns recordsCount
            every {
                counter(
                    "test-scenario",
                    "test-step",
                    "rabbitmq-produce-bytes",
                    refEq(metersTags)
                )
            } returns recordsByteCount
            every { recordsByteCount.report(any()) } returns recordsByteCount

            every {
                counter(
                    "test-scenario",
                    "test-step",
                    "rabbitmq-produce-failed-records",
                    refEq(metersTags)
                )
            } returns failureCounter
            every { failureCounter.report(any()) } returns failureCounter

            every {
                counter(
                    "test-scenario",
                    "test-step",
                    "rabbitmq-produce-success-records",
                    refEq(metersTags)
                )
            } returns successCounter
            every { successCounter.report(any()) } returns successCounter
            every {
                counter(
                    "test-scenario",
                    "test-step",
                    "rabbitmq-produce-success-bytes",
                    refEq(metersTags)
                )
            } returns successByteCounter
            every { successByteCounter.report(any()) } returns successByteCounter
        }
        rabbitMqProducerStep = RabbitMqProducerStep(
            stepName = "test-step",
            retryPolicy = retryPolicy,
            rabbitMqProducer = rabbitMqProducer,
            recordFactory = { _, _ -> data },
            eventsLogger = eventsLogger,
            meterRegistry = meterRegistry
        )

        // when
        rabbitMqProducerStep.start(stepStartStopContext)

        verifyOnce {
            meterRegistry.counter("test-scenario", "test-step", "rabbitmq-produce-bytes", refEq(metersTags))
            meterRegistry.counter("test-scenario", "test-step", "rabbitmq-produce-records", refEq(metersTags))
            meterRegistry.counter("test-scenario", "test-step", "rabbitmq-produce-failed-bytes", refEq(metersTags))
            meterRegistry.counter("test-scenario", "test-step", "rabbitmq-produce-success-bytes", refEq(metersTags))
            meterRegistry.counter("test-scenario", "test-step", "rabbitmq-produce-success-records", refEq(metersTags))
            meterRegistry.counter("test-scenario", "test-step", "rabbitmq-produce-failed-records", refEq(metersTags))
        }

        rabbitMqProducerStep.stop(stepStartStopContext)
    }

    @Test
    fun `should execute the step`() = testDispatcherProvider.runTest {
        // given
        val eventsTags: Map<String, String> = mockk()
        val metersTags: Map<String, String> = mockk()
        val stepStartStopContext = relaxedMockk<StepStartStopContext> {
            every { toEventTags() } returns eventsTags
            every { toMetersTags() } returns metersTags
            every { scenarioName } returns "test-scenario"
            every { stepName } returns "test-step"
        }
        val meterRegistry = relaxedMockk<CampaignMeterRegistry> {
            every {
                counter(
                    "test-scenario",
                    "test-step",
                    "rabbitmq-produce-records",
                    refEq(metersTags)
                )
            } returns recordsCount
            every { recordsCount.report(any()) } returns recordsCount
            every {
                counter(
                    "test-scenario",
                    "test-step",
                    "rabbitmq-produce-bytes",
                    refEq(metersTags)
                )
            } returns recordsByteCount
            every { recordsByteCount.report(any()) } returns recordsByteCount

            every {
                counter(
                    "test-scenario",
                    "test-step",
                    "rabbitmq-produce-failed-records",
                    refEq(metersTags)
                )
            } returns failureCounter
            every { failureCounter.report(any()) } returns failureCounter

            every {
                counter(
                    "test-scenario",
                    "test-step",
                    "rabbitmq-produce-success-records",
                    refEq(metersTags)
                )
            } returns successCounter
            every { successCounter.report(any()) } returns successCounter
            every {
                counter(
                    "test-scenario",
                    "test-step",
                    "rabbitmq-produce-success-bytes",
                    refEq(metersTags)
                )
            } returns successByteCounter
            every { successByteCounter.report(any()) } returns successByteCounter
        }
        rabbitMqProducerStep = RabbitMqProducerStep(
            stepName = "test-step",
            retryPolicy = retryPolicy,
            rabbitMqProducer = rabbitMqProducer,
            recordFactory = { _, _ -> data },
            eventsLogger = eventsLogger,
            meterRegistry = meterRegistry
        )
        rabbitMqProducerStep.start(stepStartStopContext)
        val resultSlot = slot<RabbitMqProducerResult<Any>>()
        coEvery { context.receive() } returns "This is the input"
        coJustRun { context.send(capture(resultSlot)) }

        // when
        rabbitMqProducerStep.execute(context)

        // then
        coVerifyOnce {
            context.receive()
            rabbitMqProducer.execute(data)
            eventsLogger.info(
                "rabbitmq.produce.success-response-time",
                any<Array<Any>>(),
                timestamp = any(),
                tags = any<Map<String, String>>()
            )
        }

        assertThat(resultSlot.captured).all {
            prop(RabbitMqProducerResult<*>::input).isEqualTo("This is the input")
            prop(RabbitMqProducerResult<*>::records).isEqualTo(data)
        }
    }

    @Test
    fun `should produce failed message`() = testDispatcherProvider.runTest {
        // given
        val eventsTags: Map<String, String> = mockk()
        val metersTags: Map<String, String> = mockk()
        val stepStartStopContext = relaxedMockk<StepStartStopContext> {
            every { toEventTags() } returns eventsTags
            every { toMetersTags() } returns metersTags
            every { scenarioName } returns "test-scenario"
            every { stepName } returns "test-step"
        }
        val meterRegistry = relaxedMockk<CampaignMeterRegistry> {
            every {
                counter(
                    "test-scenario",
                    "test-step",
                    "rabbitmq-produce-records",
                    refEq(metersTags)
                )
            } returns recordsCount
            every { recordsCount.report(any()) } returns recordsCount
            every {
                counter(
                    "test-scenario",
                    "test-step",
                    "rabbitmq-produce-bytes",
                    refEq(metersTags)
                )
            } returns recordsByteCount
            every { recordsByteCount.report(any()) } returns recordsByteCount

            every {
                counter(
                    "test-scenario",
                    "test-step",
                    "rabbitmq-produce-failed-records",
                    refEq(metersTags)
                )
            } returns failureCounter
            every { failureCounter.report(any()) } returns failureCounter

            every {
                counter(
                    "test-scenario",
                    "test-step",
                    "rabbitmq-produce-success-records",
                    refEq(metersTags)
                )
            } returns successCounter
            every { successCounter.report(any()) } returns successCounter
            every {
                counter(
                    "test-scenario",
                    "test-step",
                    "rabbitmq-produce-success-bytes",
                    refEq(metersTags)
                )
            } returns successByteCounter
            every { successByteCounter.report(any()) } returns successByteCounter
        }
        rabbitMqProducerStep = RabbitMqProducerStep(
            stepName = "test-step",
            retryPolicy = retryPolicy,
            rabbitMqProducer = rabbitMqProducer,
            recordFactory = { _, _ -> data },
            eventsLogger = eventsLogger,
            meterRegistry = meterRegistry
        )
        val exception = RuntimeException()
        every { rabbitMqProducer.execute(any()) } throws exception
        rabbitMqProducerStep.start(stepStartStopContext)
        coEvery { context.receive() } returns "This is the input"

        // when
        val caughtException = assertThrows<RuntimeException> {
            rabbitMqProducerStep.execute(context)
        }

        // then
        assertThat(caughtException).isSameAs(exception)
        coVerifyOnce {
            context.receive()
            rabbitMqProducer.execute(data)
            eventsLogger.warn(
                "rabbitmq.produce.failure-response-time",
                any<Array<Any>>(),
                timestamp = any(),
                tags = any<Map<String, String>>()
            )
        }
        coVerifyNever {
            context.send(any())
        }
    }
}