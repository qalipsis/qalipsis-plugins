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

package io.qalipsis.plugins.http

import assertk.all
import assertk.assertThat
import assertk.assertions.containsOnly
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.isNotSameInstanceAs
import assertk.assertions.isNull
import assertk.assertions.isSameInstanceAs
import assertk.assertions.prop
import io.mockk.every
import io.mockk.mockk
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.steps.StepCreationContext
import io.qalipsis.api.steps.StepCreationContextImpl
import io.qalipsis.plugins.http.catadioptre.sharedProviders
import io.qalipsis.plugins.http.connectionProvider.impl.OnDemandConnectionProvider
import io.qalipsis.plugins.http.connectionProvider.impl.WarmupConnectionProvider
import io.qalipsis.plugins.http.request.HttpMethod
import io.qalipsis.plugins.http.request.HttpRequest
import io.qalipsis.plugins.http.request.HttpRequestBuilder
import io.qalipsis.plugins.http.request.SimpleHttpRequest
import io.qalipsis.plugins.http.response.HttpBodyDeserializer
import io.qalipsis.plugins.http.response.ResponseConverter
import io.qalipsis.test.assertk.prop
import io.qalipsis.test.assertk.typedProp
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.steps.AbstractStepSpecificationConverterTest
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

    private val bodyDeserializer1: HttpBodyDeserializer = mockk {
        every { order } returns -1
    }

    private val bodyDeserializer2: HttpBodyDeserializer = mockk {
        every { order } returns 1
    }

    // List of serializers in the reverse order.
    private val deserializers = listOf(bodyDeserializer2, bodyDeserializer1)

    @Test
    override fun `should support expected spec`() {
        // when+then
        assertTrue(converter.support(mockk<HttpClientStepSpecificationImpl<*, *>>()))
    }

    @Test
    override fun `should not support unexpected spec`() {
        // when+then
        assertFalse(converter.support(mockk()))
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
        assertThat(creationContext.createdStep).isNotNull().isInstanceOf(HttpClientStep::class).all {
            prop(HttpClientStep<*, *>::name).isEqualTo("my-step")
            prop(HttpClientStep<*, *>::retryPolicy).isSameInstanceAs(mockedRetryPolicy)
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
        assertThat(creationContext.createdStep).isNotNull().isInstanceOf(HttpClientStep::class).all {
            prop(HttpClientStep<*, *>::name).isNotNull()
            prop(HttpClientStep<*, *>::retryPolicy).isNull()
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
    internal fun `should convert spec with name and inject a default connection provider if not configured`() =
        testDispatcherProvider.runTest {
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
            }.deserialize(Entity::class)
            val creationContext = StepCreationContextImpl(scenarioSpecification, directedAcyclicGraph, spec)

            // when
            converter.convert<String, Int>(creationContext as StepCreationContext<HttpClientStepSpecificationImpl<*, *>>)

            // then
            assertThat(creationContext.createdStep).isNotNull().isInstanceOf(HttpClientStep::class).all {
                prop(HttpClientStep<*, *>::name).isEqualTo("my-step")
                prop(HttpClientStep<*, *>::retryPolicy).isSameInstanceAs(mockedRetryPolicy)
                prop("requestFactory").isSameInstanceAs(requestSpecification)
                prop("clientConfiguration").isSameInstanceAs(spec.connectionConfiguration)
                prop("connectionProvider").isNotNull().isInstanceOf(OnDemandConnectionProvider::class).all {
                    prop("httpClientConfiguration").isSameInstanceAs(spec.connectionConfiguration)
                }
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
    internal fun `should convert spec and re-use the same instance of connection provider when shared`() =
        testDispatcherProvider.runTest {
            // given
            val requestSpecification: suspend HttpRequestBuilder.(StepContext<*, *>, String) -> HttpRequest<*> =
                { _, _ -> SimpleHttpRequest(HttpMethod.HEAD, "/head") }
            val connectionConfig = mockk<HttpClientConfiguration>()
            val existingProvider = WarmupConnectionProvider(true, connectionConfig)
            converter.sharedProviders()[ConnectionStrategyConfiguration(
                shared = true,
                strategyType = ConnectionStrategyType.WARMUP
            )] = existingProvider
            val spec = HttpClientStepSpecificationImpl<String, Int>()
            spec.apply {
                name = "my-step"
                retryPolicy = mockedRetryPolicy
                connect {
                    connectionStrategy {
                        shared = true
                        strategyType = ConnectionStrategyType.WARMUP
                    }
                }
                request(requestSpecification)
                monitoring {
                    meters = false
                }
            }.deserialize(Entity::class)
            val creationContext = StepCreationContextImpl(scenarioSpecification, directedAcyclicGraph, spec)

            // when
            converter.convert<String, Int>(creationContext as StepCreationContext<HttpClientStepSpecificationImpl<*, *>>)

            // then
            assertThat(creationContext.createdStep).isNotNull().isInstanceOf(HttpClientStep::class).all {
                prop(HttpClientStep<*, *>::name).isEqualTo("my-step")
                prop(HttpClientStep<*, *>::retryPolicy).isSameInstanceAs(mockedRetryPolicy)
                prop("requestFactory").isSameInstanceAs(requestSpecification)
                prop("clientConfiguration").isSameInstanceAs(spec.connectionConfiguration)
                prop("connectionProvider").isNotNull().isSameInstanceAs(existingProvider)
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
    internal fun `should convert spec and create a new instance of connection provider when not shared`() =
        testDispatcherProvider.runTest {
            // given
            val requestSpecification: suspend HttpRequestBuilder.(StepContext<*, *>, String) -> HttpRequest<*> =
                { _, _ -> SimpleHttpRequest(HttpMethod.HEAD, "/head") }
            val connectionConfig = mockk<HttpClientConfiguration>()
            val existingProvider = WarmupConnectionProvider(true, connectionConfig)
            converter.sharedProviders()[ConnectionStrategyConfiguration(
                shared = true,
                strategyType = ConnectionStrategyType.WARMUP
            )] = existingProvider
            val spec = HttpClientStepSpecificationImpl<String, Int>()
            spec.apply {
                name = "my-step"
                retryPolicy = mockedRetryPolicy
                connect {
                    connectionStrategy {
                        shared = false
                        strategyType = ConnectionStrategyType.WARMUP
                    }
                }
                request(requestSpecification)
                monitoring {
                    meters = false
                }
            }.deserialize(Entity::class)
            val creationContext = StepCreationContextImpl(scenarioSpecification, directedAcyclicGraph, spec)

            // when
            converter.convert<String, Int>(creationContext as StepCreationContext<HttpClientStepSpecificationImpl<*, *>>)

            // then
            assertThat(creationContext.createdStep).isNotNull().isInstanceOf(HttpClientStep::class).all {
                prop(HttpClientStep<*, *>::name).isEqualTo("my-step")
                prop(HttpClientStep<*, *>::retryPolicy).isSameInstanceAs(mockedRetryPolicy)
                prop("requestFactory").isSameInstanceAs(requestSpecification)
                prop("clientConfiguration").isSameInstanceAs(spec.connectionConfiguration)
                prop("connectionProvider").isNotNull().isNotSameInstanceAs(existingProvider)
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

    private data class Entity(val field: String)
}
