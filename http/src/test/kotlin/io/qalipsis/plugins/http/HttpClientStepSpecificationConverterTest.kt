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
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.isNotSameInstanceAs
import assertk.assertions.isNull
import assertk.assertions.isSameInstanceAs
import assertk.assertions.prop
import io.aerisconsulting.catadioptre.getProperty
import io.mockk.every
import io.mockk.mockk
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.steps.StepCreationContext
import io.qalipsis.api.steps.StepCreationContextImpl
import io.qalipsis.plugins.http.catadioptre.sharedProviders
import io.qalipsis.plugins.http.connectionProvider.ConnectionProvider
import io.qalipsis.plugins.http.connectionProvider.SharedConnectionProvider
import io.qalipsis.plugins.http.connectionProvider.impl.OnDemandConnectionProvider
import io.qalipsis.plugins.http.connectionProvider.impl.PoolConnectionProvider
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
import java.time.Duration
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
    internal fun `given two shared steps with the same configuration when converting then they share one provider`() =
        testDispatcherProvider.runTest {
            // given
            converter.sharedProviders().clear()
            val spec1 = sharedPoolSpec("step-1") { url("http://localhost:8080") }
            val spec2 = sharedPoolSpec("step-2") { url("http://localhost:8080") }

            // when
            val provider1 = convertAndGetProvider(spec1)
            val provider2 = convertAndGetProvider(spec2)

            // then
            assertThat(provider1).isInstanceOf(SharedConnectionProvider::class)
            assertThat(provider2).isSameInstanceAs(provider1)
            assertThat(converter.sharedProviders()).hasSize(1)
        }

    @Test
    internal fun `given shared steps with different configurations when converting then each gets its own provider`() =
        testDispatcherProvider.runTest {
            // given
            converter.sharedProviders().clear()
            val specs = listOf(
                sharedPoolSpec("step-1") { url("http://localhost:8080") },
                sharedPoolSpec("step-2") { url("http://localhost:8081") },
                sharedPoolSpec("step-3") { url("http://localhost:8080"); maxConnTotal = 100 },
                sharedPoolSpec("step-4") { url("http://localhost:8080"); ioThreadCount = 3 },
                sharedPoolSpec("step-5") { url("http://localhost:8080"); responseTimeout = Duration.ofSeconds(42) },
                sharedPoolSpec("step-6") { url("http://localhost:8080"); tls { disableCertificateVerification = true } },
            )

            // when
            val providers = specs.map { convertAndGetProvider(it) }

            // then
            assertThat(providers.distinct()).hasSize(specs.size)
            assertThat(converter.sharedProviders()).hasSize(specs.size)
        }

    @Test
    internal fun `given two steps not shared with the same configuration when converting then each gets its own provider`() =
        testDispatcherProvider.runTest {
            // given
            converter.sharedProviders().clear()
            val spec1 = poolSpec("step-1", shared = false) { url("http://localhost:8080") }
            val spec2 = poolSpec("step-2", shared = false) { url("http://localhost:8080") }

            // when
            val provider1 = convertAndGetProvider(spec1)
            val provider2 = convertAndGetProvider(spec2)

            // then
            assertThat(provider1).isInstanceOf(PoolConnectionProvider::class)
            assertThat(provider2).isNotSameInstanceAs(provider1)
            assertThat(converter.sharedProviders()).isEmpty()
        }

    private fun sharedPoolSpec(name: String, connection: HttpClientConfiguration.() -> Unit) =
        poolSpec(name, shared = true, connection)

    private fun poolSpec(name: String, shared: Boolean, connection: HttpClientConfiguration.() -> Unit) =
        HttpClientStepSpecificationImpl<String, String>().apply {
            this.name = name
            request { _, _ -> SimpleHttpRequest(HttpMethod.GET, "/") }
            connect {
                connection()
                connectionStrategy {
                    this.shared = shared
                    strategyType = ConnectionStrategyType.POOL
                }
            }
        }

    private suspend fun convertAndGetProvider(spec: HttpClientStepSpecificationImpl<String, String>): ConnectionProvider {
        val creationContext = StepCreationContextImpl(scenarioSpecification, directedAcyclicGraph, spec)
        converter.convert<String, String>(creationContext as StepCreationContext<HttpClientStepSpecificationImpl<*, *>>)
        return (creationContext.createdStep as HttpClientStep<*, *>).getProperty("connectionProvider")
    }

    private data class Entity(val field: String)
}
