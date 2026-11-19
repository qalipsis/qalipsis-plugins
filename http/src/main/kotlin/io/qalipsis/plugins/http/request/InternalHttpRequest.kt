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
