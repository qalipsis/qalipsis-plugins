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

package io.qalipsis.plugins.http.connectionProvider

import assertk.assertThat
import assertk.assertions.isEqualTo
import io.mockk.every
import io.mockk.mockk
import io.qalipsis.api.context.StepContext
import io.qalipsis.plugins.http.HttpClientConfiguration
import io.qalipsis.plugins.http.connectionProvider.impl.OnDemandConnectionProvider
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.apache.hc.client5.http.async.methods.SimpleHttpRequest
import org.apache.hc.client5.http.async.methods.SimpleHttpResponse
import org.apache.hc.core5.concurrent.FutureCallback
import org.apache.hc.core5.http.HttpVersion
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * @author Francisca Eze
 */
internal class OnDemandConnectionProviderIntegrationTest {

    private lateinit var provider: OnDemandConnectionProvider

    private lateinit var clientConfiguration: HttpClientConfiguration

    @BeforeEach
    fun setUp() {
        clientConfiguration = mockk {
            every { socketTimeout } returns Duration.ofSeconds(5)
            every { socksProxyAddress } returns null
            every { socksProxyUsername } returns null
            every { socksProxyPassword } returns null
            every { proxyConfiguration } returns null
            every { followRedirections } returns false
            every { version } returns HttpVersion.HTTP_1_1
            every { isSecure } returns false
        }

        provider = OnDemandConnectionProvider(clientConfiguration)
    }

    @Test
    fun `client should execute a real HTTP request`() {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(200))
        server.start()

        val acquireContext = mockk<StepContext<*, *>> {
            every { minionId } returns "minion-1"
        }
        val client = provider.acquire(acquireContext)

        val request = SimpleHttpRequest("GET", server.url("/").toString())
        val responseFuture = CompletableFuture<SimpleHttpResponse>()

        client.execute(
            request,
            object : FutureCallback<SimpleHttpResponse> {
                override fun completed(result: SimpleHttpResponse) {
                    responseFuture.complete(result)
                }

                override fun failed(ex: Exception) {
                    responseFuture.completeExceptionally(ex)
                }

                override fun cancelled() {
                    responseFuture.cancel(true)
                }
            }
        )

        val response = responseFuture.get(2, TimeUnit.SECONDS)
        assertThat(response.code).isEqualTo(200)

        val releaseContext = mockk<StepContext<*, *>> {
            every { minionId } returns "minion-1"
        }
        provider.release(releaseContext, client)
        server.shutdown()
    }
}
