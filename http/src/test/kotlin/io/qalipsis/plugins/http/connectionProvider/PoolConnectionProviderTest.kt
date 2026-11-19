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
import assertk.assertions.contains
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.isSameInstanceAs
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.context.StepStartStopContext
import io.qalipsis.plugins.http.HttpClientConfiguration
import io.qalipsis.plugins.http.HttpConnectionException
import io.qalipsis.plugins.http.connectionProvider.impl.PoolConnectionProvider
import io.qalipsis.plugins.http.connectionProvider.impl.catadioptre.initialized
import io.qalipsis.test.mockk.WithMockk
import java.time.Duration
import okhttp3.mockwebserver.MockWebServer
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient
import org.apache.hc.core5.http.HttpVersion
import org.apache.hc.core5.pool.PoolConcurrencyPolicy
import org.apache.hc.core5.reactor.IOReactorStatus
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

@WithMockk
internal class PoolConnectionProviderTest {

    @MockK
    private lateinit var clientConfiguration: HttpClientConfiguration

    @MockK
    private lateinit var stepContext: StepContext<*, *>

    @MockK
    private lateinit var stepStartStopContext: StepStartStopContext

    private lateinit var provider: PoolConnectionProvider

    private lateinit var server: MockWebServer

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()

        every { clientConfiguration.socketTimeout } returns Duration.ofSeconds(30)
        every { clientConfiguration.idleConnectionTimeout } returns Duration.ofSeconds(30)
        every { clientConfiguration.maxConnTotal } returns 10
        every { clientConfiguration.maxConnPerRoute } returns 5
        every { clientConfiguration.poolConcurrencyPolicy } returns PoolConcurrencyPolicy.STRICT
        every { clientConfiguration.version } returns HttpVersion.HTTP_1_1

        every { clientConfiguration.socksProxyAddress } returns null
        every { clientConfiguration.socksProxyUsername } returns null
        every { clientConfiguration.socksProxyPassword } returns null
        every { clientConfiguration.proxyConfiguration } returns null
        every { clientConfiguration.tlsConfiguration } returns null
        every { clientConfiguration.followRedirections } returns false
        every { clientConfiguration.connectTimeout } returns Duration.ofMillis(500)
        every { clientConfiguration.isSecure } returns false
        every { clientConfiguration.host } returns "localhost"
        every { clientConfiguration.port } returns server.port

        provider = PoolConnectionProvider(
            shared = false,
            httpClientConfiguration = clientConfiguration
        )
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `init should create the client`() {
        provider.init(stepStartStopContext)

        val client = provider.acquire(stepContext)

        assertThat(provider.initialized()).isNotNull()
        assertThat(client).isNotNull()
        assertThat(client).isInstanceOf(CloseableHttpAsyncClient::class)
    }

    @Test
    fun `init should not recreate a client`() {
        provider.init(stepStartStopContext)
        val client1 = provider.acquire(stepContext)

        provider.init(stepStartStopContext)
        val client2 = provider.acquire(stepContext)

        assertThat(client1).isSameInstanceAs(client2)
    }

    @Test
    fun `acquire should always return the same client`() {
        provider.init(stepStartStopContext)

        val client1 = provider.acquire(stepContext)
        val client2 = provider.acquire(stepContext)

        assertThat(client1).isSameInstanceAs(client2)
    }

    @Test
    fun `shutdown should close the client`() {
        provider.init(stepStartStopContext)
        val client = provider.acquire(stepContext)

        provider.shutdown(stepStartStopContext)

        assertThat(client.status).isEqualTo(IOReactorStatus.SHUT_DOWN)
    }

    @Test
    fun `init should fail when the server cannot be reached`() {
        every { clientConfiguration.port } returns PORT_WITHOUT_SERVER

        val exception = assertThrows<HttpConnectionException> { provider.init(stepStartStopContext) }

        assertThat(exception.message!!).contains("http://localhost:$PORT_WITHOUT_SERVER")
    }

    @Test
    fun `init should fail when the socks proxy cannot be reached`() {
        every { clientConfiguration.socksProxyAddress } returns "localhost:$PORT_WITHOUT_SERVER"
        every { clientConfiguration.socksProxyUsername } returns "user"
        every { clientConfiguration.socksProxyPassword } returns "password"

        provider = PoolConnectionProvider(
            shared = false,
            httpClientConfiguration = clientConfiguration
        )

        assertThrows<HttpConnectionException> { provider.init(stepStartStopContext) }
    }

    private companion object {

        /**
         * Nothing listens there, so the connections to that port cannot be established.
         */
        const val PORT_WITHOUT_SERVER = 1

    }
}
