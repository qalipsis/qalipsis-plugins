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

import io.qalipsis.plugins.http.ConnectionStrategyType
import io.qalipsis.plugins.http.HttpClientConfiguration
import io.qalipsis.plugins.http.HttpProxyConfiguration
import java.time.Duration
import org.apache.hc.core5.http.HttpVersion
import org.apache.hc.core5.pool.PoolConcurrencyPolicy

/**
 * Immutable key of a connection provider shared by several steps: steps can only share a provider when all the
 * settings that configure its client are equal.
 *
 * The settings that only apply to the requests, such as the context path or the charset, are not part of the key.
 *
 * @author Eric Jessé
 */
internal data class SharedConnectionProviderKey(
    val strategyType: ConnectionStrategyType,
    val version: HttpVersion,
    val scheme: String,
    val host: String,
    val port: Int,
    val connectTimeout: Duration,
    val responseTimeout: Duration,
    val idleConnectionTimeout: Duration,
    val noDelay: Boolean,
    val followRedirections: Boolean,
    val maxRedirections: Int,
    val maxConnPerRoute: Int,
    val maxConnTotal: Int,
    val poolConcurrencyPolicy: PoolConcurrencyPolicy,
    val ioThreadCount: Int,
    val tls: TlsKey?,
    val proxy: HttpProxyConfiguration?,
    val socksProxyAddress: String?,
    val socksProxyUsername: String?,
    val socksProxyPassword: String?,
) {

    /**
     * Values of a [io.qalipsis.plugins.http.connection.TlsConfiguration], with the arrays converted to lists to
     * compare them by content.
     */
    data class TlsKey(
        val disableCertificateVerification: Boolean,
        val disableHostnameVerification: Boolean,
        val protocols: List<String>,
        val ciphers: List<String>,
    )

    companion object {

        /**
         * Creates the key from a snapshot of [configuration], which is not referenced by the key.
         */
        fun of(configuration: HttpClientConfiguration) = with(configuration) {
            SharedConnectionProviderKey(
                strategyType = connectionStrategyConfiguration.strategyType,
                version = version,
                scheme = scheme,
                host = host,
                port = port,
                connectTimeout = connectTimeout,
                responseTimeout = responseTimeout,
                idleConnectionTimeout = idleConnectionTimeout,
                noDelay = noDelay,
                followRedirections = followRedirections,
                maxRedirections = maxRedirections,
                maxConnPerRoute = maxConnPerRoute,
                maxConnTotal = maxConnTotal,
                poolConcurrencyPolicy = poolConcurrencyPolicy,
                ioThreadCount = ioThreadCount,
                tls = tlsConfiguration?.let {
                    TlsKey(
                        it.disableCertificateVerification,
                        it.disableHostnameVerification,
                        it.protocols.toList(),
                        it.ciphers.toList()
                    )
                },
                proxy = proxyConfiguration?.copy(),
                socksProxyAddress = socksProxyAddress,
                socksProxyUsername = socksProxyUsername,
                socksProxyPassword = socksProxyPassword,
            )
        }
    }
}
