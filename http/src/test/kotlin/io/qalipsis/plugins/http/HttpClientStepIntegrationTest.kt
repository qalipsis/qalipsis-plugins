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

package io.qalipsis.plugins.http

import assertk.assertThat
import assertk.assertions.isEqualTo
import io.mockk.mockk
import io.mockk.spyk
import io.qalipsis.api.context.StepContext
import io.qalipsis.plugins.http.connectionProvider.ConnectionProvider
import io.qalipsis.plugins.http.connectionProvider.impl.OnDemandConnectionProvider
import io.qalipsis.plugins.http.request.HttpMethod
import io.qalipsis.plugins.http.request.HttpRequest
import io.qalipsis.plugins.http.request.HttpRequestBuilder
import io.qalipsis.plugins.http.request.SimpleHttpRequest
import io.qalipsis.plugins.http.response.HttpResponse
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
                spyk(StepTestHelper.createStepContext<String, HttpResponse<String>>(input = "This is a test"))
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
                (stepContext.output as Channel<StepContext.StepOutputRecord<HttpResponse<String>>>).receive().value
            step.stop(mockk())
            assertThat(output.reason).isEqualTo("OK")
            assertThat(output.code).isEqualTo(200)
            assertThat(output.body).isEqualTo("hello world")
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
                spyk(StepTestHelper.createStepContext<String, HttpResponse<String>>(input = "ignored-input"))
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
                (stepContext.output as Channel<StepContext.StepOutputRecord<HttpResponse<String>>>).receive().value
            assertThat(response.code).isEqualTo(500)
            assertThat(response.reason).isEqualTo("Internal Server Error")
            assertThat(response.body).isEqualTo(null)
        }

}
