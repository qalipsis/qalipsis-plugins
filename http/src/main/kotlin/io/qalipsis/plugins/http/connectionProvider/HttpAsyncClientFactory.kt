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

import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.plugins.http.HttpClientConfiguration
import io.qalipsis.plugins.http.HttpConnectionException
import io.qalipsis.plugins.http.HttpProxyType
import io.qalipsis.plugins.http.client.RequestMonitoringInterceptor
import io.qalipsis.plugins.http.client.ResponseMonitoringInterceptor
import java.net.InetSocketAddress
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import org.apache.hc.client5.http.HttpRoute
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials
import org.apache.hc.client5.http.config.ConnectionConfig
import org.apache.hc.client5.http.config.RequestConfig
import org.apache.hc.client5.http.config.TlsConfig
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient
import org.apache.hc.client5.http.impl.async.HttpAsyncClientBuilder
import org.apache.hc.client5.http.impl.async.HttpAsyncClients
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManager
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManagerBuilder
import org.apache.hc.client5.http.nio.AsyncClientConnectionManager
import org.apache.hc.client5.http.nio.AsyncConnectionEndpoint
import org.apache.hc.client5.http.protocol.HttpClientContext
import org.apache.hc.client5.http.ssl.ClientTlsStrategyBuilder
import org.apache.hc.client5.http.ssl.DefaultClientTlsStrategy
import org.apache.hc.client5.http.ssl.NoopHostnameVerifier
import org.apache.hc.core5.http.HttpHost
import org.apache.hc.core5.http.URIScheme
import org.apache.hc.core5.http.impl.DefaultConnectionReuseStrategy
import org.apache.hc.core5.http.nio.ssl.TlsStrategy
import org.apache.hc.core5.http2.HttpVersionPolicy
import org.apache.hc.core5.io.CloseMode
import org.apache.hc.core5.pool.PoolReusePolicy
import org.apache.hc.core5.reactor.ConnectionInitiator
import org.apache.hc.core5.reactor.IOReactorConfig
import org.apache.hc.core5.ssl.SSLContextBuilder
import org.apache.hc.core5.ssl.TrustStrategy
import org.apache.hc.core5.util.TimeValue
import org.apache.hc.core5.util.Timeout

/**
 * Factory to build [CloseableHttpAsyncClient] instances from [HttpClientConfiguration],
 * handling TLS, proxy, redirect, and monitoring interceptor wiring.
 *
 * @author Eric Jessé
 */
internal object HttpAsyncClientFactory {

    /**
     * Maximal count of connections opened concurrently by [preconnect].
     */
    private const val PRECONNECT_PARALLELISM = 128

    /**
     * Creates a client opening a new connection for each request and closing it once the response is received.
     * Suitable for the [io.qalipsis.plugins.http.connectionProvider.impl.OnDemandConnectionProvider].
     *
     * The client and its I/O threads are meant to be shared by all the minions: the count of concurrent connections
     * is therefore not limited by the pool, but only by the count of minions executing a request at the same time.
     */
    fun createOnDemandClient(config: HttpClientConfiguration): CloseableHttpAsyncClient {
        return buildClientBuilder(config)
            .setIOReactorConfig(buildIOReactorConfig(config))
            .setConnectionManager(buildConnectionManager(config, Int.MAX_VALUE))
            .setConnectionManagerShared(false)
            .setConnectionReuseStrategy { _, _, _ -> false }
            .build()
            .apply { start() }
    }

    /**
     * Creates a pooled client with a connection manager.
     * Suitable for [io.qalipsis.plugins.http.connectionProvider.impl.PoolConnectionProvider]
     * and [io.qalipsis.plugins.http.connectionProvider.impl.WarmupConnectionProvider].
     *
     * @param maxConnections when set, overrides the maximal counts of connections of the configuration
     */
    fun createPooledClient(config: HttpClientConfiguration, maxConnections: Int? = null): PooledClient {
        val connectionManager = buildConnectionManager(config, maxConnections)

        val client = buildClientBuilder(config)
            .setIOReactorConfig(buildIOReactorConfig(config))
            .setConnectionManager(connectionManager)
            .evictExpiredConnections()
            .evictIdleConnections(TimeValue.ofSeconds(config.idleConnectionTimeout.toSeconds()))
            .setConnectionManagerShared(false)
            .setConnectionReuseStrategy(DefaultConnectionReuseStrategy())
            .build()
            .apply { start() }

        return PooledClient(client, connectionManager)
    }

    /**
     * Creates a client whose connections can be dedicated to a single holder each, see [DedicatedConnectionManager],
     * and opens [count] dedicated connections.
     * Suitable for the [io.qalipsis.plugins.http.connectionProvider.impl.WarmupConnectionProvider].
     *
     * All the connections share the I/O reactor of the client, with [HttpClientConfiguration.ioThreadCount] threads.
     * The idle connections are not evicted: they are reopened by their holder when needed.
     */
    fun createDedicatedClient(config: HttpClientConfiguration, count: Int): DedicatedClient {
        // The TLS strategy is shared by all the connections, to avoid building an SSL context for each of them.
        val tlsStrategy = buildTlsStrategy(config) ?: DefaultClientTlsStrategy.createDefault()
        val connectionManager = DedicatedConnectionManager(
            dedicatedConnectionManagerFactory = { buildConnectionManager(config, 1, tlsStrategy) },
            sharedConnectionManager = buildConnectionManager(config, tlsStrategy = tlsStrategy)
        )
        val client = buildClientBuilder(config)
            .setIOReactorConfig(buildIOReactorConfig(config))
            .setConnectionManager(connectionManager)
            .setConnectionManagerShared(false)
            .setConnectionReuseStrategy(DefaultConnectionReuseStrategy())
            .build()
            .apply { start() }
        val connections = List(count) { connectionManager.createDedicatedConnection() }
        try {
            // The dedicated connections never expire while idle: they wait for their minion.
            preconnect(client, connectionManager, config, connections, TimeValue.NEG_ONE_MILLISECOND)
        } catch (e: Exception) {
            client.close(CloseMode.IMMEDIATE)
            throw e
        }
        return DedicatedClient(client, connectionManager, connections)
    }

    /**
     * Establishes [count] connections to the configured route and releases them to the pool.
     *
     * The pooled client of Apache HTTP Client opens its sockets on demand, so without this the TCP and TLS
     * handshakes are paid by the first requests of the minions and inflate the latencies measured at the
     * beginning of the campaign. Doing it here moves that cost to the start-up phase of the step.
     * The connections are opened by batches of [PRECONNECT_PARALLELISM], so that the start-up of large pools
     * does not last for the sum of all the handshakes.
     *
     * When none of the connections can be established, the target is considered unreachable and a
     * [HttpConnectionException] is thrown: the step start-up then fails, the warm-up of the campaign fails and the
     * campaign does not start. Running a campaign against an unreachable server only produces a full report of
     * failures, which is more expensive to read than an immediate and explicit error.
     *
     * When only a part of the connections could be established, the missing ones are created on demand and the
     * event is logged as a warning.
     */
    fun preconnect(pooled: PooledClient, config: HttpClientConfiguration, count: Int) = preconnect(
        pooled.client,
        pooled.connectionManager,
        config,
        List(count) { null },
        TimeValue.ofMilliseconds(config.idleConnectionTimeout.toMillis())
    )

    /**
     * Establishes one connection per element of [states], leased from [connectionManager] with that state, and
     * releases them as valid for [validity], see [preconnect].
     */
    private fun preconnect(
        client: CloseableHttpAsyncClient,
        connectionManager: AsyncClientConnectionManager,
        config: HttpClientConfiguration,
        states: List<Any?>,
        validity: TimeValue,
    ) {
        val initiator = client as? ConnectionInitiator
        if (states.isEmpty() || initiator == null) {
            return
        }
        val route = buildRoute(config)
        val timeout = Timeout.ofMilliseconds(config.connectTimeout.toMillis())
        // The endpoints are all leased before any of them is released, otherwise the same connection is
        // reused for each round and a single socket is opened.
        val endpoints = mutableListOf<AsyncConnectionEndpoint>()
        var failure: Exception? = null
        try {
            states.chunked(PRECONNECT_PARALLELISM).forEach { batch ->
                if (failure == null) {
                    failure = connectBatch(connectionManager, initiator, route, timeout, batch, endpoints)
                }
            }
        } finally {
            endpoints.forEach { endpoint ->
                kotlin.runCatching { connectionManager.release(endpoint, null, validity) }
            }
        }
        failure?.let { reportPreconnectionFailure(route, states.size, endpoints.size, it) }
    }

    /**
     * Leases and connects one endpoint per element of [states] concurrently, adds the connected ones to [endpoints]
     * and returns the first failure if any.
     */
    private fun connectBatch(
        connectionManager: AsyncClientConnectionManager,
        initiator: ConnectionInitiator,
        route: HttpRoute,
        timeout: Timeout,
        states: List<Any?>,
        endpoints: MutableList<AsyncConnectionEndpoint>,
    ): Exception? {
        val leases = states.mapIndexed { index, state ->
            connectionManager.lease("preconnect-${endpoints.size + index}", route, state, timeout, null)
        }
        var failure: Exception? = null
        val connections = leases.mapNotNull { lease ->
            try {
                lease.get(timeout.toMilliseconds(), TimeUnit.MILLISECONDS).also { endpoints += it }
            } catch (e: Exception) {
                failure = failure ?: e
                lease.cancel(true)
                null
            }
        }.map { endpoint ->
            endpoint to connectionManager.connect(endpoint, initiator, timeout, null, HttpClientContext.create(), null)
        }
        connections.forEach { (endpoint, connection) ->
            try {
                connection.get(timeout.toMilliseconds(), TimeUnit.MILLISECONDS)
            } catch (e: Exception) {
                failure = failure ?: e
                // The endpoint is not connected, it is released immediately and not counted.
                endpoints -= endpoint
                kotlin.runCatching { connectionManager.release(endpoint, null, TimeValue.ZERO_MILLISECONDS) }
            }
        }
        return failure
    }

    private fun reportPreconnectionFailure(route: HttpRoute, count: Int, established: Int, failure: Exception) {
        if (established == 0) {
            throw HttpConnectionException(
                "None of the $count connections to $route could be opened before the campaign: ${failure.message}",
                failure
            )
        }
        log.warn("Only $established connections out of $count could be opened to $route before the campaign: ${failure.message}")
    }

    private fun buildRoute(config: HttpClientConfiguration): HttpRoute {
        val scheme = if (config.isSecure) URIScheme.HTTPS.id else URIScheme.HTTP.id
        val target = HttpHost(scheme, config.host, config.port)
        val proxy = config.proxyConfiguration
            ?.takeIf { it.type == HttpProxyType.HTTP }
            ?.let { HttpHost(URIScheme.HTTP.id, it.host, it.port) }

        return if (proxy == null) HttpRoute(target, null, config.isSecure)
        else HttpRoute(target, null, proxy, config.isSecure)
    }

    /**
     * Configuration of the requests: the response timeout applies to the inactivity while waiting for the response.
     */
    internal fun buildRequestConfig(config: HttpClientConfiguration): RequestConfig = RequestConfig.custom()
        .setResponseTimeout(Timeout.ofMilliseconds(config.responseTimeout.toMillis()))
        .setRedirectsEnabled(config.followRedirections)
        .setMaxRedirects(config.maxRedirections)
        .build()

    /**
     * Configuration of the connections of the pool: timeout to connect, and inactivity timeout of the sockets.
     */
    internal fun buildConnectionConfig(config: HttpClientConfiguration): ConnectionConfig = ConnectionConfig.custom()
        .setConnectTimeout(Timeout.ofMilliseconds(config.connectTimeout.toMillis()))
        .setSocketTimeout(Timeout.ofMilliseconds(config.responseTimeout.toMillis()))
        .build()

    internal fun buildIOReactorConfig(config: HttpClientConfiguration): IOReactorConfig {
        return IOReactorConfig.custom()
            .setSoTimeout(Timeout.ofMilliseconds(config.responseTimeout.toMillis()))
            .setIoThreadCount(config.ioThreadCount)
            .setTcpNoDelay(config.noDelay)
            .setSoKeepAlive(true)
            .setSoReuseAddress(true)
            .apply {
                // SOCKS proxy via IOReactor (legacy fields on HttpClientConfiguration).
                val socksProxyAddress = config.socksProxyAddress
                if (socksProxyAddress != null) {
                    setSocksProxyAddress(
                        InetSocketAddress(
                            socksProxyAddress.substringBefore(':'),
                            socksProxyAddress.substringAfter(':', "1080").toInt()
                        )
                    )
                }
                config.socksProxyUsername?.let { setSocksProxyUsername(it) }
                config.socksProxyPassword?.let { setSocksProxyPassword(it) }

                // SOCKS5 proxy via proxyConfiguration.
                val proxyConfig = config.proxyConfiguration
                if (proxyConfig != null && proxyConfig.type == HttpProxyType.SOCKS5 && socksProxyAddress == null) {
                    setSocksProxyAddress(InetSocketAddress(proxyConfig.host, proxyConfig.port))
                    proxyConfig.username?.let { setSocksProxyUsername(it) }
                    proxyConfig.password?.let { setSocksProxyPassword(it) }
                }
            }
            .build()
    }

    private fun buildConnectionManager(
        config: HttpClientConfiguration,
        maxConnections: Int? = null,
        tlsStrategy: TlsStrategy? = buildTlsStrategy(config),
    ): PoolingAsyncClientConnectionManager = PoolingAsyncClientConnectionManagerBuilder.create()
        .setMaxConnPerRoute(maxConnections ?: config.maxConnPerRoute.coerceAtMost(config.maxConnTotal))
        .setMaxConnTotal(maxConnections ?: config.maxConnTotal)
        .setDefaultConnectionConfig(buildConnectionConfig(config))
        .setPoolConcurrencyPolicy(config.poolConcurrencyPolicy)
        .setConnPoolPolicy(PoolReusePolicy.FIFO)
        .setDefaultTlsConfig(
            TlsConfig.custom()
                .setVersionPolicy(
                    if (config.version.major == 2) HttpVersionPolicy.NEGOTIATE else HttpVersionPolicy.FORCE_HTTP_1
                )
                .build()
        )
        .apply { tlsStrategy?.let { setTlsStrategy(it) } }
        .build()

    /**
     * Builds the TLS strategy from the TLS configuration, if any.
     */
    private fun buildTlsStrategy(config: HttpClientConfiguration): TlsStrategy? = config.tlsConfiguration?.let { tls ->
        val sslContextBuilder = SSLContextBuilder.create()
        if (tls.disableCertificateVerification) {
            sslContextBuilder.loadTrustMaterial(TrustStrategy { _: Array<X509Certificate>, _: String -> true })
        }
        val tlsStrategyBuilder = ClientTlsStrategyBuilder.create()
            .setSslContext(sslContextBuilder.build())
        if (tls.disableHostnameVerification) {
            tlsStrategyBuilder.setHostnameVerifier(NoopHostnameVerifier.INSTANCE)
        }
        if (tls.protocols.isNotEmpty()) {
            tlsStrategyBuilder.setTlsVersions(*tls.protocols)
        }
        if (tls.ciphers.isNotEmpty()) {
            tlsStrategyBuilder.setCiphers(*tls.ciphers)
        }
        tlsStrategyBuilder.build()
    }

    private fun buildClientBuilder(config: HttpClientConfiguration): HttpAsyncClientBuilder {
        val builder = HttpAsyncClients.custom()
            .addRequestInterceptorLast(RequestMonitoringInterceptor())
            .addResponseInterceptorLast(ResponseMonitoringInterceptor())

        // Set HTTP version policy at the client level for HTTP/2 support.
        if (config.version.major == 2) {
            builder.setVersionPolicy(HttpVersionPolicy.NEGOTIATE)
        }

        // HTTP proxy via proxyConfiguration.
        val proxyConfig = config.proxyConfiguration
        if (proxyConfig != null && proxyConfig.type == HttpProxyType.HTTP) {
            builder.setProxy(HttpHost(URIScheme.HTTP.id, proxyConfig.host, proxyConfig.port))
            if (proxyConfig.username != null && proxyConfig.password != null) {
                val credentialsProvider = BasicCredentialsProvider()
                credentialsProvider.setCredentials(
                    org.apache.hc.client5.http.auth.AuthScope(proxyConfig.host, proxyConfig.port),
                    UsernamePasswordCredentials(proxyConfig.username, proxyConfig.password!!.toCharArray())
                )
                builder.setDefaultCredentialsProvider(credentialsProvider)
            }
        }

        builder.setDefaultRequestConfig(buildRequestConfig(config))
        if (!config.followRedirections) {
            builder.disableRedirectHandling()
        }

        return builder
    }

    @JvmStatic
    private val log = logger()

    /**
     * A client with connections dedicated to single holders, and the connections opened before the campaign.
     */
    internal data class DedicatedClient(
        val client: CloseableHttpAsyncClient,
        val connectionManager: DedicatedConnectionManager,
        val connections: List<DedicatedConnectionManager.DedicatedConnection>,
    )

    /**
     * A pooled client and the connection manager that owns its pool.
     */
    internal data class PooledClient(
        val client: CloseableHttpAsyncClient,
        val connectionManager: PoolingAsyncClientConnectionManager,
    )
}
