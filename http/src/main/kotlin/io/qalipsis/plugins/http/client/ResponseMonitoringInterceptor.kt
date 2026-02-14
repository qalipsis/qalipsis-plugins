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

package io.qalipsis.plugins.http.client

import org.apache.hc.core5.http.EntityDetails
import org.apache.hc.core5.http.HttpResponse
import org.apache.hc.core5.http.HttpResponseInterceptor
import org.apache.hc.core5.http.protocol.HttpContext

/**
 * Implementation of [ResponseMonitoringInterceptor] in charge of completing the [HttpContext] with monitoring details.
 */
internal class ResponseMonitoringInterceptor : HttpResponseInterceptor {

    override fun process(response: HttpResponse, entity: EntityDetails?, context: HttpContext) {
        context.setAttribute(RECEIVED_BYTES_ATTR, entity?.contentLength ?: 0L)
    }

    companion object {

        const val RECEIVED_BYTES_ATTR = "monitoring.received-bytes"
    }
}