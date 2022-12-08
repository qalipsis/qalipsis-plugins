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
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.Tag
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.slot
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.context.StepStartStopContext
import io.qalipsis.api.events.EventsLogger
import io.qalipsis.api.meters.CampaignMeterRegistry
import io.qalipsis.api.retry.RetryPolicy
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.coVerifyNever
import io.qalipsis.test.mockk.coVerifyOnce
import io.qalipsis.test.mockk.verifyExactly
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

    @RelaxedMockK
    private lateinit var meterRegistry: CampaignMeterRegistry

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
        val stepName = "test-step"
        rabbitMqProducerStep = RabbitMqProducerStep(
            stepName = stepName,
            retryPolicy = retryPolicy,
            rabbitMqProducer = rabbitMqProducer,
            recordFactory = { _, _ -> data },
            eventsLogger = eventsLogger,
            meterRegistry = meterRegistry
        )
        val stepStartStopContext =
            StepStartStopContext(campaignKey = "1", scenarioName = "1", dagId = "1", stepName = stepName)

        // when
        rabbitMqProducerStep.start(stepStartStopContext)

        verifyOnce {
            meterRegistry.counter("rabbitmq-produce.${stepName}-bytes", any<Iterable<Tag>>())
            meterRegistry.counter("rabbitmq-produce.${stepName}-records", any<Iterable<Tag>>())
            meterRegistry.counter("rabbitmq-produce.${stepName}-failed-bytes", any<Iterable<Tag>>())
            meterRegistry.counter("rabbitmq-produce.${stepName}-failed-records", any<Iterable<Tag>>())
        }

        // when
        rabbitMqProducerStep.stop(stepStartStopContext)

        // then
        verifyExactly(4) {
            meterRegistry.remove(any<Counter>())
        }
    }

    @Test
    fun `should execute the step`() = testDispatcherProvider.runTest {
        // given
        val stepName = "test-step"
        rabbitMqProducerStep = RabbitMqProducerStep(
            stepName = stepName,
            retryPolicy = retryPolicy,
            rabbitMqProducer = rabbitMqProducer,
            recordFactory = { _, _ -> data },
            eventsLogger = eventsLogger,
            meterRegistry = meterRegistry
        )
        val stepStartStopContext =
            StepStartStopContext(campaignKey = "1", scenarioName = "1", dagId = "1", stepName = stepName)
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
                "rabbitmq-produce.${stepName}.success-response-time",
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
        val stepName = "test-step"
        val exception = RuntimeException()
        every { rabbitMqProducer.execute(any()) } throws exception
        rabbitMqProducerStep = RabbitMqProducerStep(
            stepName = stepName,
            retryPolicy = retryPolicy,
            rabbitMqProducer = rabbitMqProducer,
            recordFactory = { _, _ -> data },
            eventsLogger = eventsLogger,
            meterRegistry = meterRegistry
        )
        val stepStartStopContext =
            StepStartStopContext(campaignKey = "1", scenarioName = "1", dagId = "1", stepName = stepName)
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
                "rabbitmq-produce.${stepName}.failure-response-time",
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