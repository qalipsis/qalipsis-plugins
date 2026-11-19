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

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import io.mockk.mockk
import io.mockk.spyk
import io.qalipsis.api.context.StepContext
import io.qalipsis.plugins.http.connectionProvider.ConnectionProvider
import io.qalipsis.plugins.http.connectionProvider.impl.OnDemandConnectionProvider
import io.qalipsis.plugins.http.request.HttpMethod
import io.qalipsis.plugins.http.request.HttpRequest
import io.qalipsis.plugins.http.request.HttpRequestBuilder
import io.qalipsis.plugins.http.request.SimpleHttpRequest
import io.qalipsis.plugins.http.response.JsonHttpBodyDeserializer
import io.qalipsis.plugins.http.response.ResponseConverter
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.steps.StepTestHelper
import kotlinx.coroutines.channels.Channel
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension


@WithMockk
internal class SimpleHttpClientStepIntegrationTest {

    @JvmField
    @RegisterExtension
    val testDispatcherProvider = TestDispatcherProvider()

    private lateinit var server: MockWebServer

    private lateinit var connectionProvider: ConnectionProvider

    private lateinit var clientConfiguration: HttpClientConfiguration

    private lateinit var responseConverter: ResponseConverter<String>

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        clientConfiguration = HttpClientConfiguration().apply {
            url(server.url("/").toString())
        }
        connectionProvider = OnDemandConnectionProvider(clientConfiguration)
        responseConverter = ResponseConverter(String::class, listOf(JsonHttpBodyDeserializer()))
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `should perform real http request and return converted response`() =
        testDispatcherProvider.run {
            // given
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody("hello world")
                    .addHeader("Content-Type", "text/plain")
            )

            val stepContext =
                spyk(StepTestHelper.createStepContext<String, HttpResult<String, String>>(input = "This is a test"))
            val requestFactory: suspend HttpRequestBuilder.(StepContext<*, *>, String) -> HttpRequest<*> =
                { _, _ -> SimpleHttpRequest(method = HttpMethod.GET, uri = "/test") }
            val step = HttpClientStep(
                id = "http-it-step",
                retryPolicy = null,
                ioCoroutineContext = this.coroutineContext,
                requestFactory = requestFactory,
                clientConfiguration = clientConfiguration,
                connectionProvider = connectionProvider,
                responseConverter = responseConverter,
                eventsLogger = null,
                meterRegistry = null
            )
            step.start(mockk())

            // when
            step.execute(stepContext)

            // then
            val output =
                (stepContext.output as Channel<StepContext.StepOutputRecord<HttpResult<String, String>>>).receive().value
            step.stop(mockk())
            assertThat(output.response).isNotNull()
            assertThat(output.response!!.reason).isEqualTo("OK")
            assertThat(output.response!!.code).isEqualTo(200)
            assertThat(output.response!!.body).isEqualTo("hello world")
        }

    @Test
    fun `should handle server error response`() =
        testDispatcherProvider.run {
            // given
            server.enqueue(
                MockResponse()
                    .setResponseCode(500)
                    .setBody("server error")
            )
            val stepContext =
                spyk(StepTestHelper.createStepContext<String, HttpResult<String, String>>(input = "ignored-input"))
            val requestFactory: suspend HttpRequestBuilder.(StepContext<*, *>, String) -> HttpRequest<*> =
                { _, _ -> SimpleHttpRequest(method = HttpMethod.GET, uri = "/test") }

            val step = HttpClientStep(
                id = "http-it-step-error",
                retryPolicy = null,
                ioCoroutineContext = this.coroutineContext,
                requestFactory = requestFactory,
                clientConfiguration = clientConfiguration,
                connectionProvider = connectionProvider,
                responseConverter = responseConverter,
                eventsLogger = null,
                meterRegistry = null
            )

            // when
            step.execute(stepContext)

            // then
            val response =
                (stepContext.output as Channel<StepContext.StepOutputRecord<HttpResult<String, String>>>).receive().value
            assertThat(response.response).isNotNull()
            assertThat(response.response!!.code).isEqualTo(500)
            assertThat(response.response!!.reason).isEqualTo("Internal Server Error")
            assertThat(response.response!!.body).isEqualTo(null)
        }

}
