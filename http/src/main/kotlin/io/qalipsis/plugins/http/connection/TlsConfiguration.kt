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

package io.qalipsis.plugins.http.connection

import io.qalipsis.api.annotations.Spec

/**
 * Configures the behavior of the TLS handler.
 *
 * @property disableCertificateVerification when set to true, the remote certificate is not validated during the hand-shake, default to false
 *
 * @author Eric Jessé
 */
@Spec
data class TlsConfiguration internal constructor(
    var disableCertificateVerification: Boolean = false,
    var disableHostnameVerification: Boolean = false,
) {
    internal var protocols = arrayOf<String>()

    internal var ciphers = arrayOf<String>()

    /**
     * TLS protocols to support, instead of the default ones from the TLS handler.
     */
    fun protocols(vararg protocols: String) {
        this.protocols += protocols
    }

    /**
     * TLS ciphers to support, instead of the default ones from the TLS handler.
     */
    fun ciphers(vararg ciphers: String) {
        this.ciphers += ciphers
    }

}
