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
