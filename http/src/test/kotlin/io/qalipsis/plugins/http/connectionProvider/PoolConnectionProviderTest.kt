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
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.isSameInstanceAs
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.context.StepStartStopContext
import io.qalipsis.plugins.http.HttpClientConfiguration
import io.qalipsis.plugins.http.connectionProvider.impl.PoolConnectionProvider
import io.qalipsis.plugins.http.connectionProvider.impl.catadioptre.initialized
import io.qalipsis.test.mockk.WithMockk
import java.time.Duration
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient
import org.apache.hc.core5.http.HttpVersion
import org.apache.hc.core5.pool.PoolConcurrencyPolicy
import org.apache.hc.core5.reactor.IOReactorStatus
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@WithMockk
internal class PoolConnectionProviderTest {

    @MockK
    private lateinit var clientConfiguration: HttpClientConfiguration

    @MockK
    private lateinit var stepContext: StepContext<*, *>

    @MockK
    private lateinit var stepStartStopContext: StepStartStopContext

    private lateinit var provider: PoolConnectionProvider

    @BeforeEach
    fun setUp() {
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

        provider = PoolConnectionProvider(
            shared = false,
            httpClientConfiguration = clientConfiguration
        )
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
    fun `should create pooled client with socks proxy configuration`() {
        every { clientConfiguration.socksProxyAddress } returns "localhost:1080"
        every { clientConfiguration.socksProxyUsername } returns "user"
        every { clientConfiguration.socksProxyPassword } returns "password"

        provider = PoolConnectionProvider(
            shared = false,
            httpClientConfiguration = clientConfiguration
        )

        provider.init(stepStartStopContext)
        val client = provider.acquire(stepContext)

        assertThat(client).isNotNull()

        provider.shutdown(stepStartStopContext)
    }
}
