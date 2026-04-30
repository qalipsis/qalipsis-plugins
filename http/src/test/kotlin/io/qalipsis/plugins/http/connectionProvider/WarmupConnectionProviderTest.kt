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
import assertk.assertions.contains
import assertk.assertions.doesNotContain
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.isSameInstanceAs
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.context.StepStartStopContext
import io.qalipsis.plugins.http.HttpClientConfiguration
import io.qalipsis.plugins.http.connectionProvider.impl.WarmupConnectionProvider
import io.qalipsis.plugins.http.connectionProvider.impl.catadioptre.preparedConnections
import io.qalipsis.test.mockk.WithMockk
import java.time.Duration
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient
import org.apache.hc.core5.http.HttpVersion
import org.apache.hc.core5.pool.PoolConcurrencyPolicy
import org.apache.hc.core5.reactor.IOReactorStatus.ACTIVE
import org.apache.hc.core5.reactor.IOReactorStatus.SHUT_DOWN
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@WithMockk
internal class WarmupConnectionProviderUnitTest {

    @MockK
    private lateinit var clientConfiguration: HttpClientConfiguration

    @MockK
    private lateinit var stepContext: StepContext<*, *>

    @MockK
    private lateinit var stepStartStopContext: StepStartStopContext

    private lateinit var provider: WarmupConnectionProvider

    @BeforeEach
    fun setUp() {
        every { clientConfiguration.socketTimeout } returns Duration.ofSeconds(30)
        every { clientConfiguration.idleConnectionTimeout } returns Duration.ofSeconds(30)
        every { clientConfiguration.maxConnTotal } returns 5
        every { clientConfiguration.maxConnPerRoute } returns 5
        every { clientConfiguration.poolConcurrencyPolicy } returns PoolConcurrencyPolicy.STRICT
        every { clientConfiguration.version } returns HttpVersion.HTTP_1_1

        every { clientConfiguration.socksProxyAddress } returns null
        every { clientConfiguration.socksProxyUsername } returns null
        every { clientConfiguration.socksProxyPassword } returns null
        every { clientConfiguration.proxyConfiguration } returns null
        every { clientConfiguration.tlsConfiguration } returns null
        every { clientConfiguration.followRedirections } returns false

        every { stepStartStopContext.scheduledMinionCount } returns 2
        every { stepContext.minionId } returns "minion-1"
        every { stepContext.isTail } returns false

        provider = WarmupConnectionProvider(shared = false, httpClientConfiguration = clientConfiguration)
    }

    @Test
    fun `init should prepare clients queue`() {
        //given
        assertThat(provider.preparedConnections().isNullOrEmpty())

        //when
        provider.init(stepStartStopContext)

        //then
        assertThat(provider.preparedConnections().isNotEmpty())
    }


    @Test
    fun `acquire should create a valid client`() {
        //given
         provider.init(stepStartStopContext)

        // when
        val client = provider.acquire(stepContext)

        //then
        assertThat(client).isNotNull()
        assertThat(client).isInstanceOf(CloseableHttpAsyncClient::class)
    }


    @Test
    fun `acquire should return the same client per minion`() {
        provider.init(stepStartStopContext)

        val client1 = provider.acquire(stepContext)
        val client2 = provider.acquire(stepContext)

        // Same minion always gets the same client.
        assertThat(client1).isSameInstanceAs(client2)
    }

    @Test
    fun `release should keep client active if not shared and is tail and put it back in the queue`() {
        provider.init(stepStartStopContext)
        every { stepContext.isTail } returns true

        val client = provider.acquire(stepContext)
        provider.release(stepContext, client)

        assertThat(client.status).isEqualTo(ACTIVE)
        assertThat(provider.preparedConnections()).contains(client)
    }

    @Test
    fun `release should keep client for minion if shared or not tail`() {
        provider.init(stepStartStopContext)
        every { stepContext.isTail } returns false

        val client = provider.acquire(stepContext)
        provider.release(stepContext, client)

        // Should still be active
        assertThat(client.status).isEqualTo(ACTIVE)
        assertThat(provider.preparedConnections()).doesNotContain(client)
    }

    @Test
    fun `shutdown should close all clients and clear internal structures`() {
        provider.init(stepStartStopContext)

        val client1 = provider.acquire(stepContext)
        val client2 = provider.acquire(mockk<StepContext<*, *>> { every { minionId } returns "minion-2" })

        provider.shutdown(stepStartStopContext)

        assertThat(client1.status).isEqualTo(SHUT_DOWN)
        assertThat(client2.status).isEqualTo(SHUT_DOWN)
        assertThat(provider.preparedConnections()).isEmpty()
    }

    @Test
    fun `should respect socks proxy configuration`() {
        every { clientConfiguration.socksProxyAddress } returns "localhost:1080"
        every { clientConfiguration.socksProxyUsername } returns "user"
        every { clientConfiguration.socksProxyPassword } returns "pass"

        provider = WarmupConnectionProvider(shared = false, httpClientConfiguration = clientConfiguration)
        provider.init(stepStartStopContext)

        val client = provider.acquire(stepContext)
        assertThat(client).isNotNull()
        provider.shutdown(stepStartStopContext)
    }
}
