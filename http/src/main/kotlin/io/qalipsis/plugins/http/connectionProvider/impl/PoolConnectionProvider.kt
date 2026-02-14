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

    private lateinit var httpClient: CloseableHttpAsyncClient

    override fun init(context: StepStartStopContext) {
        if (initialized.compareAndSet(false, true)) {
            httpClient = HttpAsyncClientFactory.createPooledClient(httpClientConfiguration)
        }
    }

    override fun acquire(context: StepContext<*, *>): CloseableHttpAsyncClient {
        return httpClient
    }

    override fun shutdown(context: StepStartStopContext) {
        if (initialized.get() && ::httpClient.isInitialized) {
            httpClient.initiateShutdown()
            httpClient.close()
            initialized.set(false)
        }
    }
}
