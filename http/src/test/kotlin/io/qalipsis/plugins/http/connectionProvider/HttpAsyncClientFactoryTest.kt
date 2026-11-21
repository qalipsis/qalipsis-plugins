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

import assertk.all
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isLessThan
import assertk.assertions.isTrue
import assertk.assertions.prop
import assertk.assertions.rootCause
import io.qalipsis.plugins.http.HttpClientConfiguration
import java.net.SocketTimeoutException
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.apache.hc.client5.http.async.methods.SimpleHttpRequest
import org.apache.hc.client5.http.async.methods.SimpleHttpResponse
import org.apache.hc.client5.http.config.ConnectionConfig
import org.apache.hc.client5.http.config.RequestConfig
import org.apache.hc.core5.concurrent.FutureCallback
import org.apache.hc.core5.reactor.IOReactorConfig
import org.apache.hc.core5.util.Timeout
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

internal class HttpAsyncClientFactoryTest {

    private val configuration = HttpClientConfiguration().apply {
        connectTimeout = Duration.ofSeconds(3)
        responseTimeout = Duration.ofSeconds(7)
        ioThreadCount = 3
        noDelay = false
    }

    @Test
    fun `given a configuration when building the IO reactor config then the threads and timeouts are applied`() {
        assertThat(HttpAsyncClientFactory.buildIOReactorConfig(configuration)).all {
            prop(IOReactorConfig::getIoThreadCount).isEqualTo(3)
            prop(IOReactorConfig::getSoTimeout).isEqualTo(Timeout.ofSeconds(7))
            prop("tcpNoDelay") { it.isTcpNoDelay }.isFalse()
        }
    }

    @Test
    fun `given a default configuration when building the IO reactor config then there is one thread per processor`() {
        assertThat(HttpAsyncClientFactory.buildIOReactorConfig(HttpClientConfiguration()))
            .prop(IOReactorConfig::getIoThreadCount).isEqualTo(Runtime.getRuntime().availableProcessors())
    }

    @Test
    fun `given a configuration when building the request config then the response timeout is applied`() {
        configuration.followRedirections(4)

        assertThat(HttpAsyncClientFactory.buildRequestConfig(configuration)).all {
            prop(RequestConfig::getResponseTimeout).isEqualTo(Timeout.ofSeconds(7))
            prop("redirectsEnabled") { it.isRedirectsEnabled }.isTrue()
            prop(RequestConfig::getMaxRedirects).isEqualTo(4)
        }
    }

    @Test
    fun `given a configuration when building the connection config then the connect timeout is applied`() {
        assertThat(HttpAsyncClientFactory.buildConnectionConfig(configuration)).all {
            prop(ConnectionConfig::getConnectTimeout).isEqualTo(Timeout.ofSeconds(3))
            prop(ConnectionConfig::getSocketTimeout).isEqualTo(Timeout.ofSeconds(7))
        }
    }

    @Test
    fun `given a slow server when the response timeout is reached then the request fails`() {
        // given
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(200).setHeadersDelay(SLOW_RESPONSE_SECONDS, TimeUnit.SECONDS))
        server.start()
        val config = HttpClientConfiguration().apply {
            url(server.url("/").toString())
            responseTimeout = Duration.ofMillis(300)
            ioThreadCount = 1
        }
        val pooled = HttpAsyncClientFactory.createPooledClient(config)
        val response = CompletableFuture<SimpleHttpResponse>()
        val start = System.nanoTime()

        // when
        pooled.client.execute(SimpleHttpRequest("GET", server.url("/").toString()), response.asCallback())
        val exception = assertThrows<ExecutionException> { response.get(SLOW_RESPONSE_SECONDS, TimeUnit.SECONDS) }

        // then
        assertThat(exception).rootCause().isInstanceOf(SocketTimeoutException::class)
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(SLOW_RESPONSE_SECONDS))
        pooled.client.close()
        server.shutdown()
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

        const val SLOW_RESPONSE_SECONDS = 3L
    }
}
