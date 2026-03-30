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

package io.qalipsis.plugins.netty.http

import assertk.all
import assertk.assertThat
import assertk.assertions.containsOnly
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isSameInstanceAs
import assertk.assertions.prop
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.impl.annotations.SpyK
import io.mockk.mockk
import io.netty.handler.codec.http.HttpMethod
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.steps.StepCreationContext
import io.qalipsis.api.steps.StepCreationContextImpl
import io.qalipsis.plugins.netty.EventLoopGroupSupplier
import io.qalipsis.plugins.netty.http.request.HttpRequest
import io.qalipsis.plugins.netty.http.request.SimpleHttpRequest
import io.qalipsis.plugins.netty.http.response.HttpBodyDeserializer
import io.qalipsis.plugins.netty.http.response.ResponseConverter
import io.qalipsis.plugins.netty.http.spec.HttpClientStepSpecificationImpl
import io.qalipsis.plugins.netty.socket.ConnectionStrategyType
import io.qalipsis.test.assertk.prop
import io.qalipsis.test.assertk.typedProp
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.relaxedMockk
import io.qalipsis.test.steps.AbstractStepSpecificationConverterTest
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineScope
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

@WithMockk
@Suppress("UNCHECKED_CAST")
internal class HttpClientStepSpecificationConverterTest :
    AbstractStepSpecificationConverterTest<HttpClientStepSpecificationConverter>() {

    @JvmField
    @RegisterExtension
    val testDispatcherProvider = TestDispatcherProvider()

    @RelaxedMockK
    private lateinit var eventLoopGroupSupplier: EventLoopGroupSupplier

    @RelaxedMockK
    private lateinit var ioCoroutineContext: CoroutineContext

    @RelaxedMockK
    private lateinit var ioCoroutineScope: CoroutineScope

    private val bodyDeserializer1: HttpBodyDeserializer = mockk {
        every { order } returns -1
    }

    private val bodyDeserializer2: HttpBodyDeserializer = mockk {
        every { order } returns 1
    }

    @SpyK
    // List of serializers in the reverse order.
    private var deserializers = listOf(bodyDeserializer2, bodyDeserializer1)

    @Test
    override fun `should support expected spec`() {
        // when+then
        assertTrue(converter.support(relaxedMockk<HttpClientStepSpecificationImpl<*, *>>()))
    }

    @Test
    override fun `should not support unexpected spec`() {
        // when+then
        assertFalse(converter.support(relaxedMockk()))
    }

    @Test
    internal fun `should convert spec with name and retry policy to step`() = testDispatcherProvider.runTest {
        // given
        val requestSpecification: suspend HttpRequestBuilder.(StepContext<*, *>, String) -> HttpRequest<*> =
            { _, _ -> SimpleHttpRequest(HttpMethod.HEAD, "/head") }
        val spec = HttpClientStepSpecificationImpl<String, Int>()
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
        converter.convert<String, Int>(creationContext as StepCreationContext<HttpClientStepSpecificationImpl<*, *>>)

        // then
        assertThat(creationContext.createdStep).isNotNull().isInstanceOf(SimpleHttpClientStep::class).all {
            prop(SimpleHttpClientStep<*, *>::name).isEqualTo("my-step")
            prop(SimpleHttpClientStep<*, *>::retryPolicy).isSameInstanceAs(mockedRetryPolicy)
            prop("eventLoopGroupSupplier").isSameInstanceAs(eventLoopGroupSupplier)
            prop("requestFactory").isSameInstanceAs(requestSpecification)
            prop("clientConfiguration").isSameInstanceAs(spec.connectionConfiguration)
            typedProp<ResponseConverter<*>>("responseConverter").all {
                prop("argumentType").isSameInstanceAs(String::class)
                typedProp<List<HttpBodyDeserializer>>("deserializers").containsOnly(
                    bodyDeserializer1,
                    bodyDeserializer2
                )
            }
            prop("eventsLogger").isSameInstanceAs(eventsLogger)
            prop("meterRegistry").isNull()
        }
    }

    @Test
    internal fun `should convert spec without name nor retry policy to step`() = testDispatcherProvider.runTest {
        // given
        val requestSpecification: suspend HttpRequestBuilder.(StepContext<*, *>, String) -> HttpRequest<*> =
            { _, _ -> SimpleHttpRequest(HttpMethod.HEAD, "/head") }
        val spec = HttpClientStepSpecificationImpl<String, Int>()
        spec.apply {
            request(requestSpecification)
            monitoring {
                events = false
            }
        }.deserialize(Entity::class)
        val creationContext = StepCreationContextImpl(scenarioSpecification, directedAcyclicGraph, spec)

        // when
        converter.convert<String, Int>(creationContext as StepCreationContext<HttpClientStepSpecificationImpl<*, *>>)

        // then
        assertThat(creationContext.createdStep).isNotNull().isInstanceOf(SimpleHttpClientStep::class).all {
            prop(SimpleHttpClientStep<*, *>::name).isNotNull()
            prop(SimpleHttpClientStep<*, *>::retryPolicy).isNull()
            prop("eventLoopGroupSupplier").isSameInstanceAs(eventLoopGroupSupplier)
            prop("requestFactory").isSameInstanceAs(requestSpecification)
            prop("clientConfiguration").isSameInstanceAs(spec.connectionConfiguration)
            typedProp<ResponseConverter<*>>("responseConverter").all {
                prop("argumentType").isSameInstanceAs(Entity::class)
                typedProp<List<HttpBodyDeserializer>>("deserializers").containsOnly(
                    bodyDeserializer1,
                    bodyDeserializer2
                )
            }
            prop("eventsLogger").isNull()
            prop("meterRegistry").isSameInstanceAs(meterRegistry)
        }
    }

    @Test
    internal fun `should convert spec with name and pool configuration`() = testDispatcherProvider.runTest {
        // given
        val requestSpecification: suspend HttpRequestBuilder.(StepContext<*, *>, String) -> HttpRequest<*> =
            { _, _ -> SimpleHttpRequest(HttpMethod.HEAD, "/head") }
        val spec = HttpClientStepSpecificationImpl<String, Int>()
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
        }.deserialize(Entity::class)
        val creationContext = StepCreationContextImpl(scenarioSpecification, directedAcyclicGraph, spec)

        // when
        converter.convert<String, Int>(creationContext as StepCreationContext<HttpClientStepSpecificationImpl<*, *>>)

        // then
        assertThat(creationContext.createdStep).isNotNull().isInstanceOf(PooledHttpClientStep::class).all {
            prop(PooledHttpClientStep<*, *>::name).isEqualTo("my-step")
            prop(PooledHttpClientStep<*, *>::retryPolicy).isSameInstanceAs(mockedRetryPolicy)
            prop("eventLoopGroupSupplier").isSameInstanceAs(eventLoopGroupSupplier)
            prop("requestFactory").isSameInstanceAs(requestSpecification)
            prop("clientConfiguration").isSameInstanceAs(spec.connectionConfiguration)
            prop("poolConfiguration").isSameInstanceAs(spec.connectionConfiguration.poolConfiguration)
            typedProp<ResponseConverter<*>>("responseConverter").all {
                prop("argumentType").isSameInstanceAs(Entity::class)
                typedProp<List<HttpBodyDeserializer>>("deserializers").containsOnly(
                    bodyDeserializer1,
                    bodyDeserializer2
                )
            }
            prop("eventsLogger").isSameInstanceAs(eventsLogger)
            prop("meterRegistry").isNull()
        }
    }

    @Test
    internal fun `should convert spec with warmup connection strategy`() = testDispatcherProvider.runTest {
        // given
        val requestSpecification: suspend HttpRequestBuilder.(StepContext<*, *>, String) -> HttpRequest<*> =
            { _, _ -> SimpleHttpRequest(HttpMethod.HEAD, "/head") }
        val spec = HttpClientStepSpecificationImpl<String, Int>()
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
        converter.convert<String, Int>(creationContext as StepCreationContext<HttpClientStepSpecificationImpl<*, *>>)

        // then
        assertThat(creationContext.createdStep).isNotNull().isInstanceOf(WarmupHttpClientStep::class).all {
            prop(WarmupHttpClientStep<*, *>::name).isEqualTo("my-warmup-step")
            prop(WarmupHttpClientStep<*, *>::retryPolicy).isSameInstanceAs(mockedRetryPolicy)
            prop("eventLoopGroupSupplier").isSameInstanceAs(eventLoopGroupSupplier)
            prop("requestFactory").isSameInstanceAs(requestSpecification)
            prop("clientConfiguration").isSameInstanceAs(spec.connectionConfiguration)
            typedProp<ResponseConverter<*>>("responseConverter").all {
                prop("argumentType").isSameInstanceAs(String::class)
            }
            prop("eventsLogger").isSameInstanceAs(eventsLogger)
            prop("meterRegistry").isNull()
        }
    }

    @Test
    internal fun `should convert spec with pool connection strategy`() = testDispatcherProvider.runTest {
        // given
        val requestSpecification: suspend HttpRequestBuilder.(StepContext<*, *>, String) -> HttpRequest<*> =
            { _, _ -> SimpleHttpRequest(HttpMethod.HEAD, "/head") }
        val spec = HttpClientStepSpecificationImpl<String, Int>()
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
        converter.convert<String, Int>(creationContext as StepCreationContext<HttpClientStepSpecificationImpl<*, *>>)

        // then
        assertThat(creationContext.createdStep).isNotNull().isInstanceOf(PooledHttpClientStep::class).all {
            prop(PooledHttpClientStep<*, *>::name).isEqualTo("my-pool-step")
        }
    }

    private data class Entity(val field: String)
}
