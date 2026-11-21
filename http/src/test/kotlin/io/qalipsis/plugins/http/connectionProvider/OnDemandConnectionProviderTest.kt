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
import assertk.assertions.isNotIn
import assertk.assertions.isSameInstanceAs
import io.mockk.impl.annotations.MockK
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.context.StepStartStopContext
import io.qalipsis.plugins.http.HttpClientConfiguration
import io.qalipsis.plugins.http.connectionProvider.impl.OnDemandConnectionProvider
import io.qalipsis.test.mockk.WithMockk
import org.apache.hc.core5.reactor.IOReactorStatus.SHUTTING_DOWN
import org.apache.hc.core5.reactor.IOReactorStatus.SHUT_DOWN
import org.junit.jupiter.api.Test

@WithMockk
internal class OnDemandConnectionProviderTest {

    @MockK
    private lateinit var minion1Context: StepContext<*, *>

    @MockK
    private lateinit var minion2Context: StepContext<*, *>

    @MockK
    private lateinit var stepStartStopContext: StepStartStopContext

    private val clientConfiguration = HttpClientConfiguration().apply { ioThreadCount = 1 }

    @Test
    fun `given an initialized provider when acquiring for several minions then the same running client is returned`() {
        // given
        val provider = OnDemandConnectionProvider(clientConfiguration)
        provider.init(stepStartStopContext)

        // when
        val client1 = provider.acquire(minion1Context)
        val client2 = provider.acquire(minion2Context)
        provider.release(minion1Context, client1)

        // then
        assertThat(client1).isSameInstanceAs(client2)
        assertThat(client1.status).isNotIn(SHUTTING_DOWN, SHUT_DOWN)
        provider.shutdown(stepStartStopContext)
    }

    @Test
    fun `given an initialized provider when initializing again then the client is not recreated`() {
        // given
        val provider = OnDemandConnectionProvider(clientConfiguration)
        provider.init(stepStartStopContext)
        val client1 = provider.acquire(minion1Context)

        // when
        provider.init(stepStartStopContext)

        // then
        assertThat(provider.acquire(minion1Context)).isSameInstanceAs(client1)
        provider.shutdown(stepStartStopContext)
    }

    @Test
    fun `given an initialized provider when shutting down then the client is closed`() {
        // given
        val provider = OnDemandConnectionProvider(clientConfiguration)
        provider.init(stepStartStopContext)
        val client = provider.acquire(minion1Context)

        // when
        provider.shutdown(stepStartStopContext)

        // then
        assertThat(client.status).isEqualTo(SHUT_DOWN)
    }

    @Test
    fun `given a socks proxy when initializing then the client is created without connecting`() {
        // given
        val configuration = clientConfiguration.copy(
            socksProxyAddress = "localhost",
            socksProxyUsername = "user",
            socksProxyPassword = "password"
        )
        val provider = OnDemandConnectionProvider(configuration)

        // when
        provider.init(stepStartStopContext)

        // then
        assertThat(provider.acquire(minion1Context).status).isNotIn(SHUTTING_DOWN, SHUT_DOWN)
        provider.shutdown(stepStartStopContext)
    }
}
