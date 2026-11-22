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

package io.qalipsis.plugins.http

import io.qalipsis.api.annotations.Spec
import io.qalipsis.api.constraints.PositiveDuration
import io.qalipsis.plugins.http.connection.TlsConfiguration
import java.net.InetAddress
import java.net.URI
import java.nio.charset.Charset
import java.time.Duration
import javax.validation.Valid
import javax.validation.constraints.Min
import javax.validation.constraints.NotBlank
import javax.validation.constraints.NotNull
import javax.validation.constraints.Positive
import org.apache.hc.core5.http.HttpVersion
import org.apache.hc.core5.pool.PoolConcurrencyPolicy

/**
 * Configuration of the HTTP client connecting to a peer.
 *
 * @property connectTimeout maximal duration to establish a connection (TCP and TLS handshakes), defaults to 10 seconds
 * @property responseTimeout maximal duration of inactivity while waiting for the response once the request is sent,
 * also used as the inactivity timeout of the sockets, defaults to 10 seconds
 * @property ioThreadCount count of I/O threads of the client: the client of a step uses its own threads, which
 * are shared by all the minions of the step, defaults to the count of available processors. Each request wakes up the
 * selector of its I/O thread (a limit of Apache HttpCore), so at very high rates the minions and the I/O threads wait
 * for the locks of the selectors: more I/O threads lower the tail latency but cost throughput and CPU (on 10 cores at
 * 1,000 minions, 20 threads: p99 −19 %, throughput −8 %; 40 threads: p99 −33 %, throughput −17 %). Prefer the Netty
 * plugin for the highest request rates.
 *
 * @author Eric Jessé
 */
@Spec
data class HttpClientConfiguration internal constructor(
    var version: HttpVersion = HttpVersion.HTTP_1_1,
    @field:NotBlank internal var scheme: String = "",
    @field:NotBlank internal var contextPath: String = "",
    @field:PositiveDuration var connectTimeout: Duration = Duration.ofSeconds(10),
    internal var noDelay: Boolean = true,
    internal var keepConnectionAlive: Boolean = true,
    @field:Valid internal var tlsConfiguration: TlsConfiguration? = null,
    @field:Valid internal var proxyConfiguration: HttpProxyConfiguration? = null,
    internal var charset: Charset = Charsets.UTF_8,
    @field:Min(1048576) internal var maxContentLength: Int = 1048576,
    internal var inflate: Boolean = false,
    internal var followRedirections: Boolean = false,
    @field:Min(1) internal var maxRedirections: Int = 10,
    @field:PositiveDuration var responseTimeout: Duration = Duration.ofSeconds(10),
    @field:PositiveDuration var shutdownTimeout: Duration = Duration.ofSeconds(10),
    @field:Positive var sendBufferSize: Int = 1024,
    @field:Positive var receiveBufferSize: Int = 1024,
    val socksProxyAddress: String? = null,
    val socksProxyUsername: String? = null,
    val socksProxyPassword: String? = null,
    var maxConnPerRoute: Int = 5,
    var maxConnTotal: Int = 25,
    var poolConcurrencyPolicy: PoolConcurrencyPolicy = PoolConcurrencyPolicy.STRICT,
    var idleConnectionTimeout: Duration = Duration.ofSeconds(30),
    @field:Positive var ioThreadCount: Int = Runtime.getRuntime().availableProcessors(),
) {

    /**
     * Former name of [responseTimeout].
     */
    @Deprecated("Use responseTimeout", ReplaceWith("responseTimeout"))
    var readTimeout: Duration
        get() = responseTimeout
        set(value) {
            responseTimeout = value
        }

    /**
     * Former timeout of the sockets, now equal to [responseTimeout].
     */
    @Deprecated("Use responseTimeout", ReplaceWith("responseTimeout"))
    val socketTimeout: Duration
        get() = responseTimeout

    internal var inetAddress = InetAddress.getLocalHost()

    internal var connectionStrategyConfiguration = ConnectionStrategyConfiguration()

    internal val isSecure: Boolean
        get() = scheme == "https"

    init {
        url("http://localhost")
    }

    @field:NotBlank
    internal var host: String = "localhost"

    @field:NotNull
    @field:Positive
    internal var port: Int = 80

    /**
     * Enables and configures the connection to the remote peer via a proxy.
     */
    fun proxy(configurationBlock: HttpProxyConfiguration.() -> Unit) {
        this.proxyConfiguration = HttpProxyConfiguration().also { it.configurationBlock() }
    }

    /**
     * Configures the root url to access to the peer HTTP server, defaults to http://localhost.
     */
    fun url(url: String) {
        val uri = URI(url)
        scheme = uri.scheme
        host = uri.host
        inetAddress = InetAddress.getByName(host)
        port = uri.port.takeIf { it > 0 } ?: (if (isSecure) 443 else 80)
        contextPath = if (uri.rawPath.endsWith("/")) {
            uri.rawPath.substringBeforeLast("/")
        } else {
            uri.rawPath
        }
    }

    /**
     * Enables the inflation of requests and responses (if supported by the server).
     */
    fun inflate() {
        inflate = true
    }

    /**
     * Configures the charset of the requests and accepted for the responses, defaults to UTF-8.
     */
    fun charset(charset: Charset) {
        this.charset = charset
    }

    /**
     * Configures the maximal length of a single response in bytes, defaults to 1048576 bytes.
     */
    fun maxContentLength(maxContentLength: Int) {
        this.maxContentLength = maxContentLength
    }

    /**
     * Enables the following of redirections received with status 3xx, defaults to disabled.
     */
    fun followRedirections(max: Int = maxRedirections) {
        this.followRedirections = true
        this.maxRedirections = max
    }

    /**
     * Configures the connection strategy for this HTTP connection.
     *
     * - ON_DEMAND: one connection per request (default)
     * - POOL: shared connection pool
     * - WARMUP: one dedicated connection per minion, opened before the campaign
     */
    fun connectionStrategy(configurationBlock: @ConnectionStrategyMarker ConnectionStrategyConfiguration.() -> Unit) {
        connectionStrategyConfiguration.configurationBlock()
    }

    /**
     * Enables and configures the connection to the remote peer with TLS.
     */
    fun tls(configurationBlock: TlsConfiguration.() -> Unit) {
        this.tlsConfiguration = TlsConfiguration()
            .also { it.configurationBlock() }
    }

    /**
     * Configures the remote host and port to send the requests.
     */
    fun address(host: String, port: Int) {
        this.inetAddress = InetAddress.getByName(host)
        this.host = host
        this.port = port
    }

    /**
     * Configures the remote address and port to send the requests.
     */
    fun address(address: InetAddress, port: Int) {
        this.inetAddress = address
        this.host = address.hostAddress
        this.port = port
    }
}
