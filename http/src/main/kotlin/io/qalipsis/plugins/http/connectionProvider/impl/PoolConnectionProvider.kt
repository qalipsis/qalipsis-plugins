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
