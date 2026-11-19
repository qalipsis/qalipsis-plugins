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

package io.qalipsis.plugins.http.connectionProvider.impl

import io.aerisconsulting.catadioptre.KTestable
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.context.StepStartStopContext
import io.qalipsis.plugins.http.HttpClientConfiguration
import io.qalipsis.plugins.http.connectionProvider.ConnectionProvider
import io.qalipsis.plugins.http.connectionProvider.HttpAsyncClientFactory
import io.qalipsis.plugins.http.connectionProvider.HttpAsyncClientFactory.PooledClient
import java.util.concurrent.atomic.AtomicBoolean
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient

/**
 * Connection provider that maintains a pool of HTTP connections shared among all minions.
 *
 * @property shared denotes if the connection should be shared among steps
 * @property httpClientConfiguration the HTTP client configuration
 *
 * @author Francisca Eze
 */
class PoolConnectionProvider(val shared: Boolean, private val httpClientConfiguration: HttpClientConfiguration) :
    ConnectionProvider {

    @KTestable
    private val initialized = AtomicBoolean(false)

    @KTestable
    private lateinit var pooledClient: PooledClient

    override fun init(context: StepStartStopContext) {
        if (initialized.compareAndSet(false, true)) {
            pooledClient = HttpAsyncClientFactory.createPooledClient(httpClientConfiguration)
            // The connections are established now, so that the minions do not pay the handshakes on their
            // first requests and the latencies of the beginning of the campaign stay comparable.
            HttpAsyncClientFactory.preconnect(
                pooledClient,
                httpClientConfiguration,
                httpClientConfiguration.maxConnPerRoute.coerceAtMost(httpClientConfiguration.maxConnTotal)
            )
        }
    }

    override fun acquire(context: StepContext<*, *>): CloseableHttpAsyncClient {
        return pooledClient.client
    }

    override fun shutdown(context: StepStartStopContext) {
        if (initialized.get() && ::pooledClient.isInitialized) {
            pooledClient.client.initiateShutdown()
            pooledClient.client.close()
            initialized.set(false)
        }
    }
}
