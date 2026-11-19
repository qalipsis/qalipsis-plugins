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
import org.apache.hc.client5.http.config.RequestConfig
import org.apache.hc.client5.http.config.TlsConfig
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient
import org.apache.hc.client5.http.impl.async.HttpAsyncClientBuilder
import org.apache.hc.client5.http.impl.async.HttpAsyncClients
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManager
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManagerBuilder
import org.apache.hc.client5.http.nio.AsyncConnectionEndpoint
import org.apache.hc.client5.http.protocol.HttpClientContext
import org.apache.hc.client5.http.ssl.ClientTlsStrategyBuilder
import org.apache.hc.client5.http.ssl.NoopHostnameVerifier
import org.apache.hc.core5.http.HttpHost
import org.apache.hc.core5.http.URIScheme
import org.apache.hc.core5.http.impl.DefaultConnectionReuseStrategy
import org.apache.hc.core5.http2.HttpVersionPolicy
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
     * Creates a minimal client with no connection pooling and no connection reuse.
     * Suitable for the [io.qalipsis.plugins.http.connectionProvider.impl.OnDemandConnectionProvider].
     */
    fun createOnDemandClient(config: HttpClientConfiguration): CloseableHttpAsyncClient {
        val ioReactorConfig = buildIOReactorConfig(config)
        val builder = buildClientBuilder(config)
            .setIOReactorConfig(ioReactorConfig)

        // A connection manager is needed for TLS and HTTP/2 ALPN negotiation.
        if (config.isSecure || config.version.major == 2) {
            val connectionManager = buildConnectionManager(config)
            builder.setConnectionManager(connectionManager)
                .setConnectionManagerShared(false)
        } else {
            builder.setConnectionReuseStrategy { _, _, _ -> false }
        }

        return builder.build().apply { start() }
    }

    /**
     * Creates a pooled client with a connection manager.
     * Suitable for [io.qalipsis.plugins.http.connectionProvider.impl.PoolConnectionProvider]
     * and [io.qalipsis.plugins.http.connectionProvider.impl.WarmupConnectionProvider].
     */
    fun createPooledClient(config: HttpClientConfiguration): PooledClient {
        val ioReactorConfig = buildIOReactorConfig(config)
        val connectionManager = buildConnectionManager(config)

        val client = buildClientBuilder(config)
            .setIOReactorConfig(ioReactorConfig)
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
     * Establishes [count] connections to the configured route and releases them to the pool.
     *
     * The pooled client of Apache HTTP Client opens its sockets on demand, so without this the TCP and TLS
     * handshakes are paid by the first requests of the minions and inflate the latencies measured at the
     * beginning of the campaign. Doing it here moves that cost to the start-up phase of the step.
     *
     * When none of the connections can be established, the target is considered unreachable and a
     * [HttpConnectionException] is thrown: the step start-up then fails, the warm-up of the campaign fails and the
     * campaign does not start. Running a campaign against an unreachable server only produces a full report of
     * failures, which is more expensive to read than an immediate and explicit error.
     *
     * When only a part of the connections could be established, the missing ones are created on demand and the
     * event is logged as a warning.
     */
    fun preconnect(pooled: PooledClient, config: HttpClientConfiguration, count: Int) {
        val initiator = pooled.client as? ConnectionInitiator
        if (count <= 0 || initiator == null) {
            return
        }
        val route = buildRoute(config)
        val timeout = Timeout.ofMilliseconds(config.connectTimeout.toMillis())
        val validity = TimeValue.ofMilliseconds(config.idleConnectionTimeout.toMillis())
        // The endpoints are all leased before any of them is released, otherwise the same connection is
        // reused for each round and a single socket is opened.
        val endpoints = mutableListOf<AsyncConnectionEndpoint>()
        var established = 0
        var failure: Exception? = null
        try {
            repeat(count) { index ->
                val endpoint = pooled.connectionManager
                    .lease("preconnect-$index", route, null, timeout, null)
                    .get(timeout.toMilliseconds(), TimeUnit.MILLISECONDS)
                endpoints += endpoint
                pooled.connectionManager
                    .connect(endpoint, initiator, timeout, null, HttpClientContext.create(), null)
                    .get(timeout.toMilliseconds(), TimeUnit.MILLISECONDS)
                established++
            }
        } catch (e: Exception) {
            failure = e
        } finally {
            endpoints.forEach { endpoint ->
                kotlin.runCatching { pooled.connectionManager.release(endpoint, null, validity) }
            }
        }

        if (failure != null) {
            if (established == 0) {
                throw HttpConnectionException(
                    "None of the $count connections to $route could be opened before the campaign: ${failure.message}",
                    failure
                )
            }
            log.warn("Only $established connections out of $count could be opened to $route before the campaign: ${failure.message}")
        }
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

    private fun buildIOReactorConfig(config: HttpClientConfiguration): IOReactorConfig {
        return IOReactorConfig.custom()
            .setSoTimeout(Timeout.ofMilliseconds(config.socketTimeout.toMillis()))
            .setIoThreadCount(1)
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

    private fun buildConnectionManager(config: HttpClientConfiguration): org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManager {
        val builder = PoolingAsyncClientConnectionManagerBuilder.create()
            .setMaxConnPerRoute(config.maxConnPerRoute.coerceAtMost(config.maxConnTotal))
            .setMaxConnTotal(config.maxConnTotal)
            .setPoolConcurrencyPolicy(config.poolConcurrencyPolicy)
            .setConnPoolPolicy(PoolReusePolicy.FIFO)
            .setDefaultTlsConfig(
                TlsConfig.custom()
                    .apply {
                        if (config.version.major == 2) {
                            setVersionPolicy(HttpVersionPolicy.NEGOTIATE)
                        } else {
                            setVersionPolicy(HttpVersionPolicy.FORCE_HTTP_1)
                        }
                    }
                    .build()
            )

        // Apply custom TLS strategy when TLS configuration is present.
        config.tlsConfiguration?.let { tls ->
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
            builder.setTlsStrategy(tlsStrategyBuilder.build())
        }

        return builder.build()
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

        // Redirect handling.
        if (config.followRedirections) {
            builder.setDefaultRequestConfig(
                RequestConfig.custom()
                    .setRedirectsEnabled(true)
                    .setMaxRedirects(config.maxRedirections)
                    .build()
            )
        } else {
            builder.disableRedirectHandling()
        }

        return builder
    }

    @JvmStatic
    private val log = logger()

    /**
     * A pooled client and the connection manager that owns its pool.
     */
    internal data class PooledClient(
        val client: CloseableHttpAsyncClient,
        val connectionManager: PoolingAsyncClientConnectionManager,
    )
}
