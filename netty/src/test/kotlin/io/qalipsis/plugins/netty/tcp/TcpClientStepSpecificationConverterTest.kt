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

package io.qalipsis.plugins.netty.tcp

import assertk.all
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isSameInstanceAs
import assertk.assertions.prop
import io.mockk.impl.annotations.RelaxedMockK
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.steps.StepCreationContext
import io.qalipsis.api.steps.StepCreationContextImpl
import io.qalipsis.plugins.netty.ByteArrayRequestBuilder
import io.qalipsis.plugins.netty.EventLoopGroupSupplier
import io.qalipsis.plugins.netty.socket.ConnectionStrategyType
import io.qalipsis.plugins.netty.tcp.spec.TcpClientStepSpecificationImpl
import io.qalipsis.test.assertk.prop
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.relaxedMockk
import io.qalipsis.test.steps.AbstractStepSpecificationConverterTest
import kotlin.coroutines.CoroutineContext
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

@Suppress("UNCHECKED_CAST")
internal class TcpClientStepSpecificationConverterTest :
    AbstractStepSpecificationConverterTest<TcpClientStepSpecificationConverter>() {

    @JvmField
    @RegisterExtension
    val testDispatcherProvider = TestDispatcherProvider()

    @RelaxedMockK
    private lateinit var eventLoopGroupSupplier: EventLoopGroupSupplier

    @RelaxedMockK
    private lateinit var ioCoroutineContext: CoroutineContext

    @Test
    override fun `should support expected spec`() {
        // when+then
        assertTrue(converter.support(relaxedMockk<TcpClientStepSpecificationImpl<*>>()))
    }

    @Test
    override fun `should not support unexpected spec`() {
        // when+then
        assertFalse(converter.support(relaxedMockk()))
    }

    @Test
    internal fun `should convert spec with name and retry policy to step`() = testDispatcherProvider.runTest {
        // given
        val requestSpecification: suspend ByteArrayRequestBuilder.(StepContext<*, *>, Int) -> ByteArray =
            { _, _ -> ByteArray(1) { it.toByte() } }
        val spec = TcpClientStepSpecificationImpl<Int>()
        spec.apply {
            name = "my-step"
            retryPolicy = mockedRetryPolicy
            request(requestSpecification)

            monitoring {
                meters = false
            }
        }
        val creationContext = StepCreationContextImpl(scenarioSpecification, directedAcyclicGraph, spec)

        // when
        converter.convert<String, Int>(creationContext as StepCreationContext<TcpClientStepSpecificationImpl<*>>)

        // then
        assertThat(creationContext.createdStep).isNotNull().isInstanceOf(SimpleTcpClientStep::class).all {
            prop(SimpleTcpClientStep<*>::name).isEqualTo("my-step")
            prop(SimpleTcpClientStep<*>::retryPolicy).isSameInstanceAs(mockedRetryPolicy)
            prop("eventLoopGroupSupplier").isSameInstanceAs(eventLoopGroupSupplier)
            prop("requestFactory").isSameInstanceAs(requestSpecification)
            prop("clientConfiguration").isSameInstanceAs(spec.connectionConfiguration)
            prop("eventsLogger").isSameInstanceAs(eventsLogger)
            prop("meterRegistry").isNull()
        }
    }

    @Test
    internal fun `should convert spec without name nor retry policy to step`() = testDispatcherProvider.runTest {
        // given
        val requestSpecification: suspend ByteArrayRequestBuilder.(StepContext<*, *>, Int) -> ByteArray =
            { _, _ -> ByteArray(1) { it.toByte() } }
        val spec = TcpClientStepSpecificationImpl<Int>()
        spec.apply {
            request(requestSpecification)
            monitoring {
                events = false
            }
        }
        val creationContext = StepCreationContextImpl(scenarioSpecification, directedAcyclicGraph, spec)

        // when
        converter.convert<String, Int>(creationContext as StepCreationContext<TcpClientStepSpecificationImpl<*>>)

        // then
        assertThat(creationContext.createdStep).isNotNull().isInstanceOf(SimpleTcpClientStep::class).all {
            prop(SimpleTcpClientStep<*>::name).isNotNull()
            prop(SimpleTcpClientStep<*>::retryPolicy).isNull()
            prop("eventLoopGroupSupplier").isSameInstanceAs(eventLoopGroupSupplier)
            prop("requestFactory").isSameInstanceAs(requestSpecification)
            prop("clientConfiguration").isSameInstanceAs(spec.connectionConfiguration)
            prop("eventsLogger").isNull()
            prop("meterRegistry").isSameInstanceAs(meterRegistry)
        }
    }

    @Test
    internal fun `should convert spec with name and pool configuration`() = testDispatcherProvider.runTest {
        // given
        val requestSpecification: suspend ByteArrayRequestBuilder.(StepContext<*, *>, Int) -> ByteArray =
            { _, _ -> ByteArray(1) { it.toByte() } }
        val spec = TcpClientStepSpecificationImpl<Int>()
        spec.apply {
            name = "my-step"
            retryPolicy = mockedRetryPolicy
            request(requestSpecification)
            connect {
                pool {
                    size = 123
                    checkHealthBeforeUse = false
                }
            }

            monitoring {
                meters = false
            }
        }
        val creationContext = StepCreationContextImpl(scenarioSpecification, directedAcyclicGraph, spec)

        // when
        converter.convert<String, Int>(creationContext as StepCreationContext<TcpClientStepSpecificationImpl<*>>)

        // then
        assertThat(creationContext.createdStep).isNotNull().isInstanceOf(PooledTcpClientStep::class).all {
            prop(PooledTcpClientStep<*>::name).isEqualTo("my-step")
            prop(PooledTcpClientStep<*>::retryPolicy).isSameInstanceAs(mockedRetryPolicy)
            prop("eventLoopGroupSupplier").isSameInstanceAs(eventLoopGroupSupplier)
            prop("requestFactory").isSameInstanceAs(requestSpecification)
            prop("clientConfiguration").isSameInstanceAs(spec.connectionConfiguration)
            prop("poolConfiguration").isSameInstanceAs(spec.connectionConfiguration.poolConfiguration)
            prop("eventsLogger").isSameInstanceAs(eventsLogger)
            prop("meterRegistry").isNull()
        }
    }

    @Test
    internal fun `should convert spec with warmup connection strategy`() = testDispatcherProvider.runTest {
        // given
        val requestSpecification: suspend ByteArrayRequestBuilder.(StepContext<*, *>, Int) -> ByteArray =
            { _, _ -> ByteArray(1) { it.toByte() } }
        val spec = TcpClientStepSpecificationImpl<Int>()
        spec.apply {
            name = "my-warmup-step"
            retryPolicy = mockedRetryPolicy
            request(requestSpecification)
            connect {
                connectionStrategy {
                    strategyType = ConnectionStrategyType.WARMUP
                    shared = true
                }
            }
            monitoring {
                meters = false
            }
        }
        val creationContext = StepCreationContextImpl(scenarioSpecification, directedAcyclicGraph, spec)

        // when
        converter.convert<String, Int>(creationContext as StepCreationContext<TcpClientStepSpecificationImpl<*>>)

        // then
        assertThat(creationContext.createdStep).isNotNull().isInstanceOf(WarmupTcpClientStep::class).all {
            prop(WarmupTcpClientStep<*>::name).isEqualTo("my-warmup-step")
            prop(WarmupTcpClientStep<*>::retryPolicy).isSameInstanceAs(mockedRetryPolicy)
            prop("eventLoopGroupSupplier").isSameInstanceAs(eventLoopGroupSupplier)
            prop("requestFactory").isSameInstanceAs(requestSpecification)
            prop("clientConfiguration").isSameInstanceAs(spec.connectionConfiguration)
            prop("eventsLogger").isSameInstanceAs(eventsLogger)
            prop("meterRegistry").isNull()
        }
    }

    @Test
    internal fun `should convert spec with pool connection strategy`() = testDispatcherProvider.runTest {
        // given
        val requestSpecification: suspend ByteArrayRequestBuilder.(StepContext<*, *>, Int) -> ByteArray =
            { _, _ -> ByteArray(1) { it.toByte() } }
        val spec = TcpClientStepSpecificationImpl<Int>()
        spec.apply {
            name = "my-pool-step"
            request(requestSpecification)
            connect {
                connectionStrategy {
                    strategyType = ConnectionStrategyType.POOL
                }
            }
        }
        val creationContext = StepCreationContextImpl(scenarioSpecification, directedAcyclicGraph, spec)

        // when
        converter.convert<String, Int>(creationContext as StepCreationContext<TcpClientStepSpecificationImpl<*>>)

        // then
        assertThat(creationContext.createdStep).isNotNull().isInstanceOf(PooledTcpClientStep::class).all {
            prop(PooledTcpClientStep<*>::name).isEqualTo("my-pool-step")
        }
    }

}
