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

package io.qalipsis.plugins.http.connectionProvider

import io.qalipsis.plugins.http.HttpClientConfiguration
import io.qalipsis.plugins.http.HttpProxyType
import io.qalipsis.plugins.http.client.RequestMonitoringInterceptor
import io.qalipsis.plugins.http.client.ResponseMonitoringInterceptor
import java.net.InetSocketAddress
import java.security.cert.X509Certificate
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials
import org.apache.hc.client5.http.config.RequestConfig
import org.apache.hc.client5.http.config.TlsConfig
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient
import org.apache.hc.client5.http.impl.async.HttpAsyncClientBuilder
import org.apache.hc.client5.http.impl.async.HttpAsyncClients
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManagerBuilder
import org.apache.hc.client5.http.ssl.ClientTlsStrategyBuilder
import org.apache.hc.client5.http.ssl.NoopHostnameVerifier
import org.apache.hc.core5.http.HttpHost
import org.apache.hc.core5.http.URIScheme
import org.apache.hc.core5.http.impl.DefaultConnectionReuseStrategy
import org.apache.hc.core5.http2.HttpVersionPolicy
import org.apache.hc.core5.pool.PoolReusePolicy
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
    fun createPooledClient(config: HttpClientConfiguration): CloseableHttpAsyncClient {
        val ioReactorConfig = buildIOReactorConfig(config)
        val connectionManager = buildConnectionManager(config)

        return buildClientBuilder(config)
            .setIOReactorConfig(ioReactorConfig)
            .setConnectionManager(connectionManager)
            .evictExpiredConnections()
            .evictIdleConnections(TimeValue.ofSeconds(config.idleConnectionTimeout.toSeconds()))
            .setConnectionManagerShared(false)
            .setConnectionReuseStrategy(DefaultConnectionReuseStrategy())
            .build()
            .apply { start() }
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
}
