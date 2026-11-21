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

package io.qalipsis.plugins.http.connectionProvider

import assertk.assertThat
import assertk.assertions.isEqualTo
import io.mockk.mockk
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.context.StepStartStopContext
import io.qalipsis.plugins.http.HttpClientConfiguration
import io.qalipsis.plugins.http.connectionProvider.impl.OnDemandConnectionProvider
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.apache.hc.client5.http.async.methods.SimpleHttpRequest
import org.apache.hc.client5.http.async.methods.SimpleHttpResponse
import org.apache.hc.core5.concurrent.FutureCallback
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * @author Francisca Eze
 */
internal class OnDemandConnectionProviderIntegrationTest {

    private lateinit var server: MockWebServer

    private lateinit var provider: OnDemandConnectionProvider

    private val stepStartStopContext = mockk<StepStartStopContext>()

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        provider = OnDemandConnectionProvider(HttpClientConfiguration().apply { url(server.url("/").toString()) })
        provider.init(stepStartStopContext)
    }

    @AfterEach
    fun tearDown() {
        provider.shutdown(stepStartStopContext)
        server.shutdown()
    }

    @Test
    fun `given a shared client when executing several requests then each request uses a new connection`() {
        // given
        repeat(REQUESTS) { server.enqueue(MockResponse().setResponseCode(200)) }
        val client = provider.acquire(mockk<StepContext<*, *>>())

        // when
        val codes = (1..REQUESTS).map {
            val response = CompletableFuture<SimpleHttpResponse>()
            client.execute(SimpleHttpRequest("GET", server.url("/").toString()), response.asCallback())
            response.get(2, TimeUnit.SECONDS).code
        }

        // then
        assertThat(codes).isEqualTo(List(REQUESTS) { 200 })
        // The sequence number is the index of the request on its connection.
        val sequenceNumbers = (1..REQUESTS).map { server.takeRequest(1, TimeUnit.SECONDS)!!.sequenceNumber }
        assertThat(sequenceNumbers).isEqualTo(List(REQUESTS) { 0 })
    }

    private fun CompletableFuture<SimpleHttpResponse>.asCallback() = object : FutureCallback<SimpleHttpResponse> {
        override fun completed(result: SimpleHttpResponse) {
            complete(result)
        }

        override fun failed(ex: Exception) {
            completeExceptionally(ex)
        }

        override fun cancelled() {
            cancel(true)
        }
    }

    private companion object {

        const val REQUESTS = 3
    }
}
