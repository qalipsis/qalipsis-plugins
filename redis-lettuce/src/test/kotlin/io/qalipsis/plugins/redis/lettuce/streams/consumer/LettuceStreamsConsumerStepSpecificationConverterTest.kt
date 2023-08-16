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

package io.qalipsis.plugins.redis.lettuce.streams.consumer

import assertk.all
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isSameAs
import io.aerisconsulting.catadioptre.invokeInvisible
import io.lettuce.core.StreamMessage
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.spyk
import io.mockk.verify
import io.qalipsis.api.context.StepStartStopContext
import io.qalipsis.api.meters.Counter
import io.qalipsis.api.steps.StepCreationContext
import io.qalipsis.api.steps.StepCreationContextImpl
import io.qalipsis.api.steps.StepMonitoringConfiguration
import io.qalipsis.api.steps.datasource.DatasourceObjectConverter
import io.qalipsis.api.steps.datasource.IterativeDatasourceStep
import io.qalipsis.api.steps.datasource.processors.NoopDatasourceObjectProcessor
import io.qalipsis.plugins.redis.lettuce.configuration.RedisConnectionType
import io.qalipsis.test.assertk.prop
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.relaxedMockk
import io.qalipsis.test.steps.AbstractStepSpecificationConverterTest
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

@Suppress("UNCHECKED_CAST")
@WithMockk
internal class LettuceStreamsConsumerStepSpecificationConverterTest :
    AbstractStepSpecificationConverterTest<LettuceStreamsConsumerStepSpecificationConverter>() {

    @JvmField
    @RegisterExtension
    val testDispatcherProvider = TestDispatcherProvider()

    @RelaxedMockK
    private lateinit var ioCoroutineScope: CoroutineScope

    @RelaxedMockK
    private lateinit var ioCoroutineDispatcher: CoroutineDispatcher

    @RelaxedMockK
    private lateinit var recordsCount: Counter

    @RelaxedMockK
    private lateinit var bytesCounter: Counter

    @Test
    override fun `should not support unexpected spec`() {
        Assertions.assertFalse(converter.support(relaxedMockk()))
    }

    @Test
    override fun `should support expected spec`() {
        Assertions.assertTrue(converter.support(relaxedMockk<LettuceStreamsConsumerStepSpecificationImpl>()))
    }

    @Test
    internal fun `should convert spec with name and queue`() = testDispatcherProvider.runTest {
        // given
        val spec = LettuceStreamsConsumerStepSpecificationImpl()
        spec.apply {
            name = "my-step"
            connection {
                nodes = listOf("localhost:6379")
                database = 1
                redisConnectionType = RedisConnectionType.CLUSTER
            }
            concurrency(2)
            streamKey("name1")
            group("group")
        }
        val creationContext = StepCreationContextImpl(scenarioSpecification, directedAcyclicGraph, spec)

        val spiedConverter = spyk(converter, recordPrivateCalls = true)
        val recordsConverter: DatasourceObjectConverter<List<StreamMessage<ByteArray, ByteArray>>, out Any?> =
            relaxedMockk()

        every {
            spiedConverter["buildConverter"](
                refEq(spec.monitoringConfig),
                refEq(spec.flattenOutput)
            )
        } returns recordsConverter

        // when
        spiedConverter.convert<Unit, Map<String, *>>(
            creationContext as StepCreationContext<LettuceStreamsConsumerStepSpecificationImpl>
        )

        // then
        creationContext.createdStep!!.let {
            assertThat(it).isInstanceOf(IterativeDatasourceStep::class).all {
                prop("name").isEqualTo("my-step")
                prop("reader").isNotNull().isInstanceOf(LettuceStreamsIterativeReader::class).all {
                    prop("ioCoroutineScope").isSameAs(ioCoroutineScope)
                    prop("ioCoroutineDispatcher").isSameAs(ioCoroutineDispatcher)
                    prop("groupName").isEqualTo("group")
                    prop("concurrency").isEqualTo(2)
                    prop("streamKey").isEqualTo("name1")
                    prop("offset").isEqualTo("0-0")
                    prop("connectionFactory").isNotNull()
                }
                prop("processor").isNotNull().isInstanceOf(NoopDatasourceObjectProcessor::class)
                prop("converter").isNotNull().isSameAs(recordsConverter)
            }
        }
    }

    @Test
    internal fun `should convert spec without name but with queue`() = testDispatcherProvider.runTest {
        // given
        val spec = LettuceStreamsConsumerStepSpecificationImpl()
        spec.apply {
            connection {
                nodes = listOf("localhost:6379")
                database = 1
                redisConnectionType = RedisConnectionType.CLUSTER
            }
            concurrency(2)
            streamKey("name2")
            group("group1")
            offset(LettuceStreamsConsumerOffset.LAST_CONSUMED)
        }
        val creationContext = StepCreationContextImpl(scenarioSpecification, directedAcyclicGraph, spec)

        val spiedConverter = spyk(converter, recordPrivateCalls = true)
        val recordsConverter: DatasourceObjectConverter<List<StreamMessage<ByteArray, ByteArray>>, out Any?> =
            relaxedMockk()

        every {
            spiedConverter["buildConverter"](
                refEq(spec.monitoringConfig),
                refEq(spec.flattenOutput)
            )
        } returns recordsConverter

        // when
        spiedConverter.convert<Unit, Map<String, *>>(
            creationContext as StepCreationContext<LettuceStreamsConsumerStepSpecificationImpl>
        )

        // then
        creationContext.createdStep!!.let {
            assertThat(it).isInstanceOf(IterativeDatasourceStep::class).all {
                prop("name").isEqualTo("")
                prop("reader").isNotNull().isInstanceOf(LettuceStreamsIterativeReader::class).all {
                    prop("ioCoroutineScope").isSameAs(ioCoroutineScope)
                    prop("ioCoroutineDispatcher").isSameAs(ioCoroutineDispatcher)
                    prop("groupName").isEqualTo("group1")
                    prop("concurrency").isEqualTo(2)
                    prop("streamKey").isEqualTo("name2")
                    prop("offset").isEqualTo(">")
                    prop("connectionFactory").isNotNull()
                }
                prop("processor").isNotNull().isInstanceOf(NoopDatasourceObjectProcessor::class)
                prop("converter").isNotNull().isSameAs(recordsConverter)
            }
        }
    }

    @Test
    internal fun `should build single converter`() {

        val monitoringConfiguration = StepMonitoringConfiguration(false, true)

        // when
        val recordsConverter =
            converter.invokeInvisible<DatasourceObjectConverter<List<StreamMessage<ByteArray, ByteArray>>, out Any?>>(
                "buildConverter",
                monitoringConfiguration,
                true
            )

        // then
        assertThat(recordsConverter).isNotNull().isInstanceOf(LettuceStreamsConsumerSingleConverter::class).all {
            prop("recordsCounter").isNull()
            prop("valuesBytesReceived").isNull()
            prop("meterRegistry").isSameAs(meterRegistry)
        }
    }

    @Test
    internal fun `should build batch converter`() {

        val monitoringConfiguration = StepMonitoringConfiguration(false, true)

        // when
        val recordsConverter =
            converter.invokeInvisible<DatasourceObjectConverter<List<StreamMessage<ByteArray, ByteArray>>, out Any?>>(
                "buildConverter",
                monitoringConfiguration,
                false
            )

        // then
        assertThat(recordsConverter).isNotNull().isInstanceOf(LettuceStreamsConsumerBatchConverter::class).all {
            prop("meterRegistry").isSameAs(meterRegistry)
            prop("recordsCounter").isNull()
            prop("valuesBytesReceived").isNull()
        }
    }

    @Test
    internal fun `should build converter with records counter and bytes counter`() {
        val monitoringConfiguration = StepMonitoringConfiguration(true, true)
        val tags: Map<String, String> = emptyMap()
        every {
            meterRegistry.counter(
                "scenario-name",
                "step-name",
                "redis-lettuce-streams-consumer-records",
                refEq(tags)
            )
        } returns recordsCount
        every { recordsCount.report(any()) } returns recordsCount
        every {
            meterRegistry.counter(
                "scenario-name",
                "step-name",
                "redis-lettuce-streams-consumer-records-bytes",
                refEq(tags)
            )
        } returns bytesCounter
        every { bytesCounter.report(any()) } returns bytesCounter
        val startStopContext = relaxedMockk<StepStartStopContext> {
            every { toEventTags() } returns tags
            every { scenarioName } returns "scenario-name"
            every { stepName } returns "step-name"
        }

        // when
        val recordsConverter =
            converter.invokeInvisible<DatasourceObjectConverter<List<StreamMessage<ByteArray, ByteArray>>, out Any?>>(
                "buildConverter",
                monitoringConfiguration,
                false
            )
        recordsConverter.start(startStopContext)
        // then
        assertThat(recordsConverter).isNotNull().isInstanceOf(LettuceStreamsConsumerBatchConverter::class).all {
            prop("meterRegistry").isSameAs(meterRegistry)
            prop("recordsCounter").isNotNull()
            prop("valuesBytesReceived").isNotNull()
        }
        verify {
            meterRegistry.counter("scenario-name", "step-name", "redis-lettuce-streams-consumer-records", tags)
            meterRegistry.counter(
                "scenario-name",
                "step-name",
                "redis-lettuce-streams-consumer-records-bytes",
                tags
            )
        }
        confirmVerified(meterRegistry)
    }
}
