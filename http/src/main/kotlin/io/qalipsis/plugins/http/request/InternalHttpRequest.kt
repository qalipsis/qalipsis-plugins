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

package io.qalipsis.plugins.http.request

import io.qalipsis.plugins.http.HttpClientConfiguration
import org.apache.hc.core5.http.message.BasicHeader
import org.apache.hc.core5.http.message.BasicNameValuePair
import org.apache.hc.core5.http.nio.AsyncRequestProducer

/**
 * Description of a request as seen internally.
 *
 * @author Francisca Eze
 */
internal interface InternalHttpRequest<SELF : HttpRequest<SELF>, HTTP_QUERY : Any> : HttpRequest<SELF> {

    /**
     * Computes the complete URI for the coming request.
     */
    fun computeUri(clientConfiguration: HttpClientConfiguration): String

    /**
     * Converts the request to an async request producer.
     */
    fun toAsyncRequest(clientConfiguration: HttpClientConfiguration): AsyncRequestProducer

    fun Map<CharSequence, Any>.toHeaders(): Array<BasicHeader> =
        map { (k, v) -> BasicHeader(k.toString(), v.toString()) }
            .toTypedArray()

    fun Map<CharSequence, Collection<CharSequence>>.toUrlParams(): Array<BasicNameValuePair> =
        flatMap { (k, values) ->
            values.map { BasicNameValuePair(k.toString(), it.toString()) }
        }.toTypedArray()
}
