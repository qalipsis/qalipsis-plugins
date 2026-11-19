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
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNotSameInstanceAs
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.qalipsis.api.context.StepContext
import io.qalipsis.plugins.http.HttpClientConfiguration
import io.qalipsis.plugins.http.connectionProvider.impl.OnDemandConnectionProvider
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.WithMockk
import java.time.Duration
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient
import org.apache.hc.core5.http.HttpVersion
import org.apache.hc.core5.reactor.IOReactorStatus.SHUT_DOWN
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

@WithMockk
internal class OnDemandConnectionProviderTest {

    @JvmField
    @RegisterExtension
    val testDispatcherProvider = TestDispatcherProvider()

    @MockK
    private lateinit var clientConfiguration: HttpClientConfiguration

    @MockK
    private lateinit var context: StepContext<*, *>

    private lateinit var provider: OnDemandConnectionProvider

    @BeforeEach
    fun setUp() {
        every { clientConfiguration.socketTimeout } returns Duration.ofSeconds(30)
        every { clientConfiguration.socksProxyAddress } returns null
        every { clientConfiguration.socksProxyUsername } returns null
        every { clientConfiguration.socksProxyPassword } returns null
        every { clientConfiguration.proxyConfiguration } returns null
        every { clientConfiguration.followRedirections } returns false
        every { clientConfiguration.version } returns HttpVersion.HTTP_1_1
        every { clientConfiguration.isSecure } returns false

        every { context.minionId } returns "test-minion"

        provider = OnDemandConnectionProvider(clientConfiguration)
    }

    @Test
    fun `acquire should return a new async client`() {
        val client = provider.acquire(context)

        assertThat(client)
            .isNotNull()
            .isInstanceOf(CloseableHttpAsyncClient::class)

        provider.release(context, client)
    }

    @Test
    fun `acquire should create a new client each time`() {
        val client1 = provider.acquire(context)
        val client2 = provider.acquire(context)

        assertThat(client1).isNotSameInstanceAs(client2)

        provider.release(context, client1)
        provider.release(context, client2)
    }

    @Test
    fun `release should close the client`() {
        val client = provider.acquire(context)

        provider.release(context, client)

        assertThat(client.status).isEqualTo(SHUT_DOWN)
    }

    @Test
    fun `releasing one client should not affect another`() {
        val client1 = provider.acquire(context)
        val client2 = provider.acquire(context)

        provider.release(context, client1)

        assertThat(client1.status).isEqualTo(SHUT_DOWN)
        assertThat(client2.status).isNotEqualTo(SHUT_DOWN)

        provider.release(context, client2)
    }

    @Test
    fun `should create client when socks proxy address is provided`() {
        every { clientConfiguration.socksProxyAddress } returns "localhost:1080"

        provider = OnDemandConnectionProvider(clientConfiguration)

        val client = provider.acquire(context)

        assertThat(client).isNotNull()

        provider.release(context, client)
    }

    @Test
    fun `should create client when socks proxy credentials are provided`() {
        every { clientConfiguration.socksProxyAddress } returns "localhost:1080"
        every { clientConfiguration.socksProxyUsername } returns "user"
        every { clientConfiguration.socksProxyPassword } returns "password"

        provider = OnDemandConnectionProvider(clientConfiguration)

        val client = provider.acquire(context)

        assertThat(client).isNotNull()

        provider.release(context, client)
    }

    @Test
    fun `should default socks proxy port to 1080 when not provided`() {
        every { clientConfiguration.socksProxyAddress } returns "localhost"

        provider = OnDemandConnectionProvider(clientConfiguration)

        val client = provider.acquire(context)

        assertThat(client).isNotNull()

        provider.release(context, client)
    }

}
