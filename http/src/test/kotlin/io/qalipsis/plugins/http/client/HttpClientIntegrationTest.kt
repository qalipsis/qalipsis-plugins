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

package io.qalipsis.plugins.http.client

import assertk.assertThat
import assertk.assertions.isEqualTo
import io.mockk.mockk
import io.mockk.spyk
import io.qalipsis.api.context.StepContext
import io.qalipsis.plugins.http.HttpClientConfiguration
import io.qalipsis.plugins.http.HttpClientStep
import io.qalipsis.plugins.http.HttpResult
import io.qalipsis.plugins.http.connectionProvider.impl.OnDemandConnectionProvider
import io.qalipsis.plugins.http.request.HttpMethod
import io.qalipsis.plugins.http.request.SimpleHttpRequest
import io.qalipsis.plugins.http.response.JsonHttpBodyDeserializer
import io.qalipsis.plugins.http.response.ResponseConverter
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.steps.StepTestHelper
import java.time.Duration
import kotlinx.coroutines.channels.Channel
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.apache.hc.core5.http.HttpHeaders
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.extension.RegisterExtension

/**
 * @author Eric Jessé
 */
@Timeout(30)
internal class DummyHttpClientIntegrationTest {

    @JvmField
    @RegisterExtension
    val testDispatcherProvider = TestDispatcherProvider()

    private lateinit var server: MockWebServer

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    @Test
    internal fun `should GET a response`() = testDispatcherProvider.run {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("OK")
                .addHeader("Content-Type", "text/plain")
        )

        val request = SimpleHttpRequest(HttpMethod.GET, "/get?param1=value1&param1=value2&param2=value3")
            .addHeader(HttpHeaders.ACCEPT, "application/json")
            .addHeader(HttpHeaders.COOKIE, "yummy_cookie=choco; tasty_cookie=strawberry")
        val config = HttpClientConfiguration().apply {
            connectTimeout = Duration.ofSeconds(4)
            url(server.url("/").toString())
        }

        val stepContext =
            spyk(StepTestHelper.createStepContext<String, HttpResult<String, String>>(input = "test"))

        val step = HttpClientStep<String, String>(
            id = "http-client-step",
            retryPolicy = null,
            ioCoroutineContext = this.coroutineContext,
            requestFactory = { _, _ -> request },
            clientConfiguration = config,
            connectionProvider = OnDemandConnectionProvider(config),
            responseConverter = ResponseConverter(String::class, listOf(JsonHttpBodyDeserializer())),
            eventsLogger = null,
            meterRegistry = null
        )
        step.start(mockk())
        step.execute(stepContext)

        val output =
            (stepContext.output as Channel<StepContext.StepOutputRecord<HttpResult<String, String>>>).receive().value
        step.stop(mockk())
        assertThat(output.response?.code).isEqualTo(200)
    }

    companion object {

        init {
            System.setProperty("QALIPSIS_LOGGING_LEVEL", "trace")
        }
    }
}
