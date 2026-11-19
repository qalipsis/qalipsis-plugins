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
import io.qalipsis.api.context.MinionId
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.context.StepStartStopContext
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.plugins.http.HttpClientConfiguration
import io.qalipsis.plugins.http.connectionProvider.ConnectionProvider
import io.qalipsis.plugins.http.connectionProvider.HttpAsyncClientFactory
import java.util.concurrent.ConcurrentHashMap
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
    private lateinit var preparedConnections: MutableList<CloseableHttpAsyncClient>

    override fun init(context: StepStartStopContext) {
        if (initialized.compareAndSet(false, true)) {
            preparedConnections = mutableListOf()
            repeat(context.scheduledMinionCount) {
                val pooledClient = HttpAsyncClientFactory.createPooledClient(httpClientConfiguration)
                // One connection per minion: the strategy promises connections ready before the start.
                HttpAsyncClientFactory.preconnect(pooledClient, httpClientConfiguration, 1)
                preparedConnections.add(pooledClient.client)
            }
        }
    }

    override fun acquire(context: StepContext<*, *>): CloseableHttpAsyncClient {
        return clients.computeIfAbsent(context.minionId) {
            preparedConnections.removeFirst()
        }
    }

    override fun release(context: StepContext<*, *>, client: HttpAsyncClient) {
        if (!shared && context.isTail) {
            preparedConnections.add(client as CloseableHttpAsyncClient)
            clients.remove(context.minionId)
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
