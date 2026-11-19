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

import io.qalipsis.plugins.http.response.HttpResponse
import java.time.Duration

/**
 * Result of an HTTP request executed by the [HttpClientStep], wrapping the actual response and
 * the meters captured during the request execution.
 *
 * @author Eric Jessé
 */
class HttpResult<I, O>(
    val connected: Boolean,
    val connectionFailure: Throwable?,
    val tlsFailure: Throwable?,
    val sendingFailure: Throwable?,
    val failure: Throwable?,
    val input: I,
    val response: HttpResponse<O>?,
    val meters: Meters,
) {

    val cause: Throwable? = failure ?: connectionFailure ?: tlsFailure ?: sendingFailure

    val isFailure: Boolean = cause != null

    val isSuccess: Boolean = !isFailure

    interface Meters {
        val sentBytes: Long
        val receivedBytes: Long
        val timeToFirstByte: Duration?
        val timeToLastByte: Duration?
        val timeToSuccessfulConnect: Duration?
        val timeToFailedConnect: Duration?
        val timeToSuccessfulTlsConnect: Duration?
        val timeToFailedTlsConnect: Duration?
    }

    internal data class MetersImpl(
        override var sentBytes: Long = 0,
        override var receivedBytes: Long = 0,
        override var timeToFirstByte: Duration? = null,
        override var timeToLastByte: Duration? = null,
        override var timeToSuccessfulConnect: Duration? = null,
        override var timeToFailedConnect: Duration? = null,
        override var timeToSuccessfulTlsConnect: Duration? = null,
        override var timeToFailedTlsConnect: Duration? = null,
    ) : Meters
}
