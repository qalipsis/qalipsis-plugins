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

package io.qalipsis.plugins.graphite.save

import assertk.all
import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.index
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isSameAs
import assertk.assertions.isTrue
import io.mockk.mockk
import io.mockk.spyk
import io.netty.channel.ChannelOutboundHandlerAdapter
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioSocketChannel
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.retry.RetryPolicy
import io.qalipsis.api.steps.StepCreationContext
import io.qalipsis.api.steps.StepCreationContextImpl
import io.qalipsis.plugins.graphite.GraphiteProtocol
import io.qalipsis.plugins.graphite.client.GraphiteClient
import io.qalipsis.plugins.graphite.client.GraphiteRecord
import io.qalipsis.plugins.graphite.client.GraphiteTcpClient
import io.qalipsis.plugins.graphite.client.codecs.PickleEncoder
import io.qalipsis.plugins.graphite.client.codecs.PlaintextEncoder
import io.qalipsis.test.assertk.prop
import io.qalipsis.test.assertk.typedProp
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.relaxedMockk
import io.qalipsis.test.steps.AbstractStepSpecificationConverterTest
import org.apache.commons.lang3.RandomStringUtils
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import kotlin.random.Random
import kotlin.reflect.KClass

/**
 *
 * @author Palina Bril
 */
@WithMockk
@Suppress("UNCHECKED_CAST")
internal class GraphiteSaveStepSpecificationConverterTest :
    AbstractStepSpecificationConverterTest<GraphiteSaveStepSpecificationConverter>() {

    @JvmField
    @RegisterExtension
    val testDispatcherProvider = TestDispatcherProvider()

    private val recordSupplier: (suspend (ctx: StepContext<*, *>, input: Any?) -> List<GraphiteRecord>) = { _, _ ->
        listOf(
            GraphiteRecord("foo", value = 1.1),
            GraphiteRecord("foo", value = 1.2),
            GraphiteRecord("foo", value = 1.3)
        )
    }

    @Test
    override fun `should not support unexpected spec`() {
        assertThat(converter.support(relaxedMockk())).isFalse()
    }

    @Test
    override fun `should support expected spec`() {
        assertThat(converter.support(relaxedMockk<GraphiteSaveStepSpecificationImpl<*>>())).isTrue()
    }

    @Test
    fun `should convert with name, retry policy and meters`() = testDispatcherProvider.runTest {
        // given
        val stepName = RandomStringUtils.randomAlphanumeric(10)
        val server = RandomStringUtils.randomAlphanumeric(10)
        val port = Random(10000).nextInt()
        val channelClass = mockk<KClass<out SocketChannel>>()
        val workerGroup = mockk<NioEventLoopGroup>()
        val retryPolicy = mockk<RetryPolicy>()

        val spec = GraphiteSaveStepSpecificationImpl<Any>()
        spec.also {
            it.name = stepName
            it.records = recordSupplier
            it.connect {
                server(server, port)
                protocol(GraphiteProtocol.PICKLE)
                netty(channelClass) { workerGroup }
            }
            it.monitoring {
                meters = true
                events = false
            }
        }.configure {
            retry(retryPolicy)
        }
        val creationContext = StepCreationContextImpl(scenarioSpecification, directedAcyclicGraph, spec)
        val spiedConverter = spyk(converter, recordPrivateCalls = true)
        // when
        spiedConverter.convert<Unit, Map<String, *>>(
            creationContext as StepCreationContext<GraphiteSaveStepSpecificationImpl<*>>
        )

        // then
        assertThat(creationContext.createdStep!!).all {
            prop("name").isEqualTo(stepName)
            prop("retryPolicy").isSameAs(retryPolicy)
            prop("messageFactory").isSameAs(recordSupplier)
            prop("eventsLogger").isNull()
            prop("meterRegistry").isSameAs(meterRegistry)
            typedProp<() -> GraphiteClient<GraphiteRecord>>("clientBuilder").transform { it.invoke() }
                .isInstanceOf<GraphiteTcpClient<GraphiteRecord>>().all {
                    prop("host").isEqualTo(server)
                    prop("port").isEqualTo(port)
                    prop("channelClass").isSameAs(channelClass)
                    prop("workerGroup").isSameAs(workerGroup)
                    typedProp<List<ChannelOutboundHandlerAdapter>>("encoders").all {
                        hasSize(1)
                        index(0).isInstanceOf<PickleEncoder>()
                    }
                }
        }
    }

    @Test
    fun `should convert with default values but with events`() = testDispatcherProvider.runTest {
        // given
        val spec = GraphiteSaveStepSpecificationImpl<Any>()
        spec.also {
            it.records = recordSupplier
            it.monitoring { events = true }
        }
        val creationContext = StepCreationContextImpl(scenarioSpecification, directedAcyclicGraph, spec)
        val spiedConverter = spyk(converter, recordPrivateCalls = true)


        // when
        spiedConverter.convert<Unit, Map<String, *>>(
            creationContext as StepCreationContext<GraphiteSaveStepSpecificationImpl<*>>
        )

        // then
        assertThat(creationContext.createdStep!!).all {
            prop("name").isNotNull()
            prop("retryPolicy").isNull()
            prop("messageFactory").isSameAs(recordSupplier)
            prop("eventsLogger").isSameAs(eventsLogger)
            prop("meterRegistry").isNull()
            typedProp<() -> GraphiteClient<GraphiteRecord>>("clientBuilder").transform { it.invoke() }
                .isInstanceOf<GraphiteTcpClient<GraphiteRecord>>().all {
                    prop("host").isEqualTo("localhost")
                    prop("port").isEqualTo(2003)
                    prop("channelClass").isSameAs(NioSocketChannel::class)
                    prop("workerGroup").isNotNull().isInstanceOf<NioEventLoopGroup>()
                    typedProp<List<ChannelOutboundHandlerAdapter>>("encoders").all {
                        hasSize(1)
                        index(0).isInstanceOf<PlaintextEncoder>()
                    }
                }
        }
    }
}
