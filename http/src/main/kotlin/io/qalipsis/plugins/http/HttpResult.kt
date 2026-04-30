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
