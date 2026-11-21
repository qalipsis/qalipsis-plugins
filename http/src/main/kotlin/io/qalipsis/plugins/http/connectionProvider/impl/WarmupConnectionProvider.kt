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
import io.qalipsis.plugins.http.HttpClientConfiguration
import io.qalipsis.plugins.http.connectionProvider.ConnectionProvider
import io.qalipsis.plugins.http.connectionProvider.DedicatedConnectionManager.DedicatedConnection
import io.qalipsis.plugins.http.connectionProvider.HttpAsyncClientFactory
import io.qalipsis.plugins.http.connectionProvider.HttpAsyncClientFactory.DedicatedClient
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient
import org.apache.hc.client5.http.protocol.HttpClientContext
import org.apache.hc.core5.io.CloseMode

/**
 * Connection provider giving each minion its own connection, opened before the campaign starts.
 *
 * One connection per scheduled minion is opened when the step starts. A minion takes one of them at its first
 * request and keeps it for its whole life, across all its iterations and replays (a replayed minion keeps its
 * identifier), like the HTTP client of a JMeter thread. No other minion uses it. The completion of a minion is
 * not observable from the step, so the connections are only released when the step stops; their count is
 * bounded by the count of minions. When the connection is closed by the server or fails, it is opened again at
 * the next request of the minion, which is recorded in the meters.
 *
 * All the connections share the I/O reactor of a single client, with [HttpClientConfiguration.ioThreadCount]
 * threads, whatever the count of minions.
 *
 * @property shared denotes if the connection should be shared among steps
 * @property httpClientConfiguration the HTTP client configuration
 *
 * @author Francisca Eze
 */
class WarmupConnectionProvider(
    val shared: Boolean,
    private val httpClientConfiguration: HttpClientConfiguration,
) : ConnectionProvider {

    @KTestable
    private val initialized = AtomicBoolean(false)

    @KTestable
    private lateinit var dedicatedClient: DedicatedClient

    /**
     * Connections opened before the campaign that no minion holds yet.
     */
    @KTestable
    private val freeConnections = ConcurrentLinkedQueue<DedicatedConnection>()

    /**
     * Connection held by each minion.
     */
    @KTestable
    private val minionConnections = ConcurrentHashMap<MinionId, DedicatedConnection>()

    override fun init(context: StepStartStopContext) {
        if (initialized.compareAndSet(false, true)) {
            try {
                dedicatedClient = HttpAsyncClientFactory.createDedicatedClient(
                    httpClientConfiguration, context.scheduledMinionCount.coerceAtLeast(1)
                )
            } catch (e: Exception) {
                initialized.set(false)
                throw e
            }
            freeConnections.addAll(dedicatedClient.connections)
        }
    }

    override fun acquire(context: StepContext<*, *>): CloseableHttpAsyncClient = dedicatedClient.client

    override fun prepare(context: StepContext<*, *>, httpContext: HttpClientContext) {
        httpContext.userToken = connectionOf(context.minionId)
    }

    /**
     * Returns the connection held by the minion, assigning it a free one - or a new one when all are held - if it
     * has none yet.
     */
    private fun connectionOf(minionId: MinionId): DedicatedConnection =
        minionConnections.computeIfAbsent(minionId) {
            freeConnections.poll() ?: dedicatedClient.connectionManager.createDedicatedConnection()
        }

    override fun shutdown(context: StepStartStopContext) {
        if (initialized.compareAndSet(true, false)) {
            // The client closes its connection manager, and with it all the dedicated connections.
            dedicatedClient.client.close(CloseMode.GRACEFUL)
            freeConnections.clear()
            minionConnections.clear()
        }
    }
}
