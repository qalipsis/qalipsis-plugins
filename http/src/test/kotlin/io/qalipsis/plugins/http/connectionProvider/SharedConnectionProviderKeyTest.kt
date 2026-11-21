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

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotEqualTo
import io.qalipsis.plugins.http.ConnectionStrategyType
import io.qalipsis.plugins.http.HttpClientConfiguration
import io.qalipsis.plugins.http.HttpProxyType
import java.time.Duration
import org.apache.hc.core5.http.HttpVersion
import org.junit.jupiter.api.Test

internal class SharedConnectionProviderKeyTest {

    @Test
    fun `given two equal configurations when creating the keys then they are equal`() {
        // given
        val configuration1 = configuration { tls { protocols("TLSv1.3") } }
        val configuration2 = configuration { tls { protocols("TLSv1.3") } }

        // when + then
        assertThat(SharedConnectionProviderKey.of(configuration2)).isEqualTo(SharedConnectionProviderKey.of(configuration1))
    }

    @Test
    fun `given configurations differing by a client setting when creating the keys then they are different`() {
        // given
        val reference = SharedConnectionProviderKey.of(configuration())
        val variants = listOf<HttpClientConfiguration.() -> Unit>(
            { url("https://localhost:8080") },
            { url("http://127.0.0.1:8080") },
            { url("http://localhost:8081") },
            { version = HttpVersion.HTTP_2 },
            { connectTimeout = Duration.ofSeconds(1) },
            { responseTimeout = Duration.ofSeconds(1) },
            { idleConnectionTimeout = Duration.ofSeconds(1) },
            { maxConnPerRoute = 1 },
            { maxConnTotal = 1 },
            { ioThreadCount = 1 },
            { followRedirections(3) },
            { tls { disableHostnameVerification = true } },
            { tls { ciphers("TLS_AES_128_GCM_SHA256") } },
            { proxy { type = HttpProxyType.SOCKS5; address("proxy", 1080) } },
            { connectionStrategy { strategyType = ConnectionStrategyType.WARMUP } },
        )

        // when
        val keys = variants.map { SharedConnectionProviderKey.of(configuration(it)) }

        // then
        keys.forEach { assertThat(it).isNotEqualTo(reference) }
    }

    @Test
    fun `given a key when the configuration changes afterwards then the key is unchanged`() {
        // given
        val configuration = configuration { tls { protocols("TLSv1.3") } }
        val key = SharedConnectionProviderKey.of(configuration)

        // when
        configuration.maxConnTotal = 1
        configuration.tls { protocols("TLSv1.2") }

        // then
        assertThat(SharedConnectionProviderKey.of(configuration)).isNotEqualTo(key)
        assertThat(key).isEqualTo(SharedConnectionProviderKey.of(configuration { tls { protocols("TLSv1.3") } }))
    }

    private fun configuration(block: HttpClientConfiguration.() -> Unit = {}) = HttpClientConfiguration().apply {
        url("http://localhost:8080")
        connectionStrategy {
            shared = true
            strategyType = ConnectionStrategyType.POOL
        }
        block()
    }
}
