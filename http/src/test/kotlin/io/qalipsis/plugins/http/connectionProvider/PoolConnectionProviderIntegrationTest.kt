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
import io.qalipsis.plugins.http.HttpClientConfiguration
import io.qalipsis.plugins.http.connectionProvider.impl.PoolConnectionProvider
import io.qalipsis.plugins.http.connectionProvider.impl.catadioptre.pooledClient
import io.qalipsis.test.mockk.WithMockk
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

@WithMockk
internal class PoolConnectionProviderIntegrationTest {

    private lateinit var provider: PoolConnectionProvider

    private lateinit var clientConfiguration: HttpClientConfiguration

    @BeforeEach
    fun setUp() {
        clientConfiguration = mockk {
            every { socketTimeout } returns Duration.ofSeconds(5)
            every { idleConnectionTimeout } returns Duration.ofSeconds(5)
            every { maxConnTotal } returns 5
            every { maxConnPerRoute } returns 5
            every { poolConcurrencyPolicy } returns PoolConcurrencyPolicy.STRICT
            every { version } returns HttpVersion.HTTP_1_1
            every { socksProxyAddress } returns null
            every { socksProxyUsername } returns null
            every { socksProxyPassword } returns null
            every { proxyConfiguration } returns null
            every { tlsConfiguration } returns null
            every { followRedirections } returns false
            every { connectTimeout } returns Duration.ofSeconds(5)
            every { isSecure } returns false
            every { host } returns "localhost"
        }

        provider = PoolConnectionProvider(
            shared = false,
            httpClientConfiguration = clientConfiguration
        )
    }

    @Test
    fun `init should open the connections of the pool before any request`() {
        val server = MockWebServer()
        server.start()
        every { clientConfiguration.port } returns server.port

        provider.init(mockk())

        // The whole pool is connected and idle, and no HTTP request was needed to open it.
        val stats = provider.pooledClient().connectionManager.totalStats
        assertThat(stats.available).isEqualTo(5)
        assertThat(stats.leased).isEqualTo(0)
        assertThat(server.requestCount).isEqualTo(0)

        provider.shutdown(mockk())
        server.shutdown()
    }

    @Test
    fun `pooled client should execute real HTTP request`() {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(200))
        server.start()
        every { clientConfiguration.port } returns server.port

        provider.init(mockk())
        val client = provider.acquire(mockk())

        val request = SimpleHttpRequest("GET", server.url("/").toString())
        val future = CompletableFuture<SimpleHttpResponse>()

        client.execute(
            request,
            object : FutureCallback<SimpleHttpResponse> {
                override fun completed(result: SimpleHttpResponse) {
                    future.complete(result)
                }

                override fun failed(ex: Exception) {
                    future.completeExceptionally(ex)
                }

                override fun cancelled() {
                    future.cancel(true)
                }
            }
        )

        val response = future.get(2, TimeUnit.SECONDS)
        assertThat(response.code).isEqualTo(200)

        provider.shutdown(mockk())
        server.shutdown()
    }

}
