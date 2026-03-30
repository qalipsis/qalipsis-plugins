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
import io.qalipsis.api.context.MinionId
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.context.StepStartStopContext
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.plugins.http.HttpClientConfiguration
import io.qalipsis.plugins.http.connectionProvider.ConnectionProvider
import io.qalipsis.plugins.http.connectionProvider.HttpAsyncClientFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean
import org.apache.hc.client5.http.async.HttpAsyncClient
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient

/**
 * Connection provider that pre-initializes a pool of HTTP connections per minion.
 *
 * @property shared denotes if the connection should be shared among steps
 * @property httpClientConfiguration the HTTP client configuration
 *
 * @author Francisca Eze
 */
class WarmupConnectionProvider(
    private val shared: Boolean,
    private val httpClientConfiguration: HttpClientConfiguration,
) : ConnectionProvider {

    private val initialized = AtomicBoolean(false)

    private val clients = ConcurrentHashMap<MinionId, CloseableHttpAsyncClient>()

    @KTestable
    private lateinit var preparedConnections: LinkedBlockingQueue<CloseableHttpAsyncClient>

    override fun init(context: StepStartStopContext) {
        if (initialized.compareAndSet(false, true)) {
            preparedConnections = LinkedBlockingQueue(context.scheduledMinionCount)
            repeat(context.scheduledMinionCount) {
                preparedConnections.put(HttpAsyncClientFactory.createPooledClient(httpClientConfiguration))
            }
        }
    }

    override fun acquire(context: StepContext<*, *>): CloseableHttpAsyncClient {
        return clients.computeIfAbsent(context.minionId) { preparedConnections.take() }
    }

    override fun release(context: StepContext<*, *>, client: HttpAsyncClient) {
        val closeableHttpAsyncClient = client as CloseableHttpAsyncClient
        if (!shared && context.isTail) {
            closeableHttpAsyncClient.close()
            initialized.set(false)
        } else {
            preparedConnections.put(closeableHttpAsyncClient)
        }
    }

    override fun shutdown(context: StepStartStopContext) {
        try {
            preparedConnections.forEach { kotlin.runCatching { it.close() } }
            clients.values.forEach { kotlin.runCatching { it.close() } }
        } catch (ex: Exception) {
            log.warn("An error occurred while shutting down the HTTP connection", ex)
        }
        initialized.set(false)
        preparedConnections.clear()
        clients.clear()
    }

    companion object {

        @JvmStatic
        private val log = logger()
    }
}
