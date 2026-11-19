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
import io.mockk.every
import io.mockk.mockk
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.context.StepStartStopContext
import io.qalipsis.plugins.http.HttpClientConfiguration
import io.qalipsis.plugins.http.connectionProvider.impl.WarmupConnectionProvider
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.apache.hc.client5.http.async.methods.SimpleHttpRequest
import org.apache.hc.client5.http.async.methods.SimpleHttpResponse
import org.apache.hc.core5.concurrent.FutureCallback
import org.apache.hc.core5.http.HttpVersion
import org.apache.hc.core5.pool.PoolConcurrencyPolicy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class WarmupConnectionProviderIntegrationTest {

    private lateinit var provider: WarmupConnectionProvider

    private lateinit var stepStartStopContext: StepStartStopContext

    private lateinit var server: MockWebServer

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()

        val config = mockk<HttpClientConfiguration> {
            every { socketTimeout } returns Duration.ofSeconds(5)
            every { idleConnectionTimeout } returns Duration.ofSeconds(5)
            every { maxConnTotal } returns 2
            every { maxConnPerRoute } returns 2
            every { poolConcurrencyPolicy } returns PoolConcurrencyPolicy.STRICT
            every { version } returns HttpVersion.HTTP_1_1
            every { socksProxyAddress } returns null
            every { socksProxyUsername } returns null
            every { socksProxyPassword } returns null
            every { proxyConfiguration } returns null
            every { tlsConfiguration } returns null
            every { followRedirections } returns false
            every { connectTimeout } returns Duration.ofMillis(500)
            every { isSecure } returns false
            every { host } returns "localhost"
            every { port } returns server.port
        }

        stepStartStopContext = mockk { every { scheduledMinionCount } returns 1 }

        provider = WarmupConnectionProvider(shared = true, config)
        provider.init(stepStartStopContext)
    }

    @Test
    fun `warmed client should execute real http request`() {
        server.enqueue(MockResponse().setResponseCode(200))

        val stepContext = mockk<StepContext<*, *>> {
            every { minionId } returns "minion-1"
            every { isTail } returns false
        }

        val client = provider.acquire(stepContext)

        val request = SimpleHttpRequest("GET", server.url("/").toString())

        val future = CompletableFuture<SimpleHttpResponse>()

        client.execute(request, object : FutureCallback<SimpleHttpResponse> {
            override fun completed(result: SimpleHttpResponse) {
                future.complete(result)
            }

            override fun failed(ex: Exception) {
                future.completeExceptionally(ex)
            }

            override fun cancelled() {
                future.cancel(true)
            }
        })

        assertThat(future.get(2, TimeUnit.SECONDS).code).isEqualTo(200)

        provider.shutdown(stepStartStopContext)
        server.shutdown()
    }
}

