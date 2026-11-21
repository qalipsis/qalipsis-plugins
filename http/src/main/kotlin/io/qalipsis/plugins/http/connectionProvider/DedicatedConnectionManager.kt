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

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicBoolean
import org.apache.hc.client5.http.HttpRoute
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManager
import org.apache.hc.client5.http.nio.AsyncClientConnectionManager
import org.apache.hc.client5.http.nio.AsyncConnectionEndpoint
import org.apache.hc.core5.concurrent.BasicFuture
import org.apache.hc.core5.concurrent.FutureCallback
import org.apache.hc.core5.http.nio.AsyncClientExchangeHandler
import org.apache.hc.core5.http.nio.AsyncPushConsumer
import org.apache.hc.core5.http.nio.HandlerFactory
import org.apache.hc.core5.http.protocol.HttpContext
import org.apache.hc.core5.io.CloseMode
import org.apache.hc.core5.reactor.ConnectionInitiator
import org.apache.hc.core5.util.TimeValue
import org.apache.hc.core5.util.Timeout

/**
 * Connection manager giving each holder of a [DedicatedConnection] its own connection, while all the connections
 * share the I/O reactor of a single client.
 *
 * A request is routed to its dedicated connection when the user token of its context is a [DedicatedConnection]
 * (see [org.apache.hc.client5.http.protocol.HttpClientContext.setUserToken]): the lookup is a simple cast, without
 * any search. Each dedicated connection is held by its own pool of one connection, which has no thread: when the
 * connection is closed by the peer or fails, the pool opens a new one for the next request of the same holder.
 * The requests without dedicated connection are served by [sharedConnectionManager].
 *
 * @property dedicatedConnectionManagerFactory creates the pool of one connection of each dedicated connection
 * @property sharedConnectionManager pool for the requests without dedicated connection
 *
 * @author Eric Jessé
 */
internal class DedicatedConnectionManager(
    private val dedicatedConnectionManagerFactory: () -> PoolingAsyncClientConnectionManager,
    private val sharedConnectionManager: PoolingAsyncClientConnectionManager,
) : AsyncClientConnectionManager {

    private val dedicatedConnections = ConcurrentLinkedQueue<DedicatedConnection>()

    /**
     * Creates a new dedicated connection. The socket is only opened when the connection is first leased.
     */
    fun createDedicatedConnection(): DedicatedConnection =
        DedicatedConnection(dedicatedConnectionManagerFactory()).also(dedicatedConnections::add)

    override fun lease(
        id: String,
        route: HttpRoute,
        state: Any?,
        requestTimeout: Timeout,
        callback: FutureCallback<AsyncConnectionEndpoint>?,
    ): Future<AsyncConnectionEndpoint> {
        val dedicated = state as? DedicatedConnection
            ?: return sharedConnectionManager.lease(id, route, state, requestTimeout, callback)
        val future = BasicFuture<AsyncConnectionEndpoint>(callback)
        // The state is kept on the connection, so that the pool of one connection always returns the same one.
        dedicated.manager.lease(id, route, dedicated, requestTimeout, object : FutureCallback<AsyncConnectionEndpoint> {
            override fun completed(result: AsyncConnectionEndpoint) {
                future.completed(DedicatedEndpoint(result, dedicated))
            }

            override fun failed(ex: Exception) {
                future.failed(ex)
            }

            override fun cancelled() {
                future.cancel()
            }
        })
        return future
    }

    override fun release(endpoint: AsyncConnectionEndpoint, newState: Any?, validDuration: TimeValue) {
        when (endpoint) {
            is DedicatedEndpoint -> endpoint.dedicated.manager.release(endpoint.delegate, endpoint.dedicated, validDuration)
            else -> sharedConnectionManager.release(endpoint, newState, validDuration)
        }
    }

    override fun connect(
        endpoint: AsyncConnectionEndpoint,
        connectionInitiator: ConnectionInitiator,
        connectTimeout: Timeout?,
        attachment: Any?,
        context: HttpContext?,
        callback: FutureCallback<AsyncConnectionEndpoint>?,
    ): Future<AsyncConnectionEndpoint> {
        if (endpoint !is DedicatedEndpoint) {
            return sharedConnectionManager.connect(endpoint, connectionInitiator, connectTimeout, attachment, context, callback)
        }
        val future = BasicFuture<AsyncConnectionEndpoint>(callback)
        endpoint.dedicated.manager.connect(
            endpoint.delegate, connectionInitiator, connectTimeout, attachment, context,
            object : FutureCallback<AsyncConnectionEndpoint> {
                override fun completed(result: AsyncConnectionEndpoint) {
                    if (endpoint.dedicated.connectedOnce.getAndSet(true)) {
                        // The dedicated connection was already open once: it was closed and is now open again.
                        context?.setAttribute(RECONNECTION_ATTR, true)
                    }
                    future.completed(endpoint)
                }

                override fun failed(ex: Exception) {
                    future.failed(ex)
                }

                override fun cancelled() {
                    future.cancel()
                }
            })
        return future
    }

    override fun upgrade(endpoint: AsyncConnectionEndpoint, attachment: Any?, context: HttpContext?) {
        when (endpoint) {
            is DedicatedEndpoint -> endpoint.dedicated.manager.upgrade(endpoint.delegate, attachment, context)
            else -> sharedConnectionManager.upgrade(endpoint, attachment, context)
        }
    }

    override fun close(closeMode: CloseMode) {
        dedicatedConnections.forEach { kotlin.runCatching { it.manager.close(closeMode) } }
        dedicatedConnections.clear()
        sharedConnectionManager.close(closeMode)
    }

    override fun close() = close(CloseMode.GRACEFUL)

    /**
     * Connection dedicated to a single holder, to set as user token of the requests of that holder.
     */
    internal class DedicatedConnection(internal val manager: PoolingAsyncClientConnectionManager) {

        /**
         * Becomes true when the connection was opened for the first time.
         */
        internal val connectedOnce = AtomicBoolean(false)
    }

    /**
     * Endpoint of a [DedicatedConnection], forwarding to the endpoint of its pool of one connection.
     */
    private class DedicatedEndpoint(
        val delegate: AsyncConnectionEndpoint,
        val dedicated: DedicatedConnection,
    ) : AsyncConnectionEndpoint() {

        override fun execute(
            id: String,
            exchangeHandler: AsyncClientExchangeHandler,
            pushHandlerFactory: HandlerFactory<AsyncPushConsumer>?,
            context: HttpContext,
        ) = delegate.execute(id, exchangeHandler, pushHandlerFactory, context)

        override fun isConnected() = delegate.isConnected

        override fun setSocketTimeout(timeout: Timeout) = delegate.setSocketTimeout(timeout)

        override fun close(closeMode: CloseMode) = delegate.close(closeMode)

        override fun getInfo() = delegate.info
    }

    companion object {

        /**
         * Attribute set in the context of a request when its dedicated connection had to be opened again.
         */
        const val RECONNECTION_ATTR = "qalipsis.http.dedicated-connection.reopened"
    }
}
