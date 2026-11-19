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

import io.aerisconsulting.catadioptre.KTestable
import io.qalipsis.plugins.http.HttpClientConfiguration
import org.apache.hc.client5.http.cookie.Cookie
import org.apache.hc.core5.http.ContentType
import org.apache.hc.core5.http.HttpHeaders
import org.apache.hc.core5.http.HttpRequest
import org.apache.hc.core5.http.message.BasicHeader
import org.apache.hc.core5.http.message.BasicNameValuePair
import org.apache.hc.core5.http.nio.AsyncRequestProducer
import org.apache.hc.core5.http.nio.support.AsyncRequestBuilder


/**
 * Standard HTTP Request with a simple body.
 *
 * @author Francisca Eze
 */
data class SimpleHttpRequest internal constructor(
    override val method: HttpMethod,
    override val uri: String
) : AbstractHttpRequest<SimpleHttpRequest>(),
    InternalHttpRequest<SimpleHttpRequest, HttpRequest> {

    private var bodyBytes: ByteArray = ByteArray(0)

    @KTestable
    private var bodyString: CharSequence? = null

    private var contentType: ContentType? = null

    override val headers = mutableMapOf<CharSequence, Any>()

    override val parameters = mutableMapOf<CharSequence, MutableCollection<CharSequence>>()

    private val cookies = mutableListOf<Cookie>()

    /**
     * Sets the [String] as body of the request. If [contentType] does not contain the charset, the charset
     * defined in the configuration of HTTP step will be added.
     *
     * @param body string to set as body of the request
     * @param contentType value for the content type header, not set if omitted.
     */
    fun body(body: CharSequence, contentType: ContentType? = null): SimpleHttpRequest {
        bodyString = body
        bodyBytes = ByteArray(0)
        this.contentType = contentType
        return this
    }

    /**
     * Sets the [ByteArray] as body of the request. If [contentType] does not contain the charset, the charset
     * defined in the configuration of HTTP step will be added.
     *
     * @param body byte array to set as body of the request
     * @param contentType value for the content type header, not set if omitted.
     */
    fun body(body: ByteArray, contentType: CharSequence? = null): SimpleHttpRequest {
        bodyBytes = body.clone()
        bodyString = null
        this.contentType = ContentType.parse(contentType)
        return this
    }

    override fun addCookies(vararg cookie: Cookie): SimpleHttpRequest {
        cookies.addAll(cookie.toList())
        return this
    }

    /**
     * Defines the content type of the body. If [contentType] does not contain the charset, the charset
     * defined in the configuration of HTTP step will be added.
     */
    fun contentType(contentType: ContentType): SimpleHttpRequest {
        this.contentType = contentType
        return this
    }

    override fun computeUri(clientConfiguration: HttpClientConfiguration): String {
        return getCompleteUri(clientConfiguration)
    }

    override fun toAsyncRequest(clientConfiguration: HttpClientConfiguration): AsyncRequestProducer {
        val parameters = parameters.flatMap { (name, values) ->
            values.map { value ->
                BasicNameValuePair(
                    name.toString(),
                    value.toString()
                )
            }
        }.toTypedArray()
        var contentTypeForEntity: ContentType? = null

        this.contentType?.also { contentType ->
            val hasSameCharset =
                contentType.charset?.name()?.equals(clientConfiguration.charset.name(), ignoreCase = true) == true
            val valueWithEncoding = if (hasSameCharset) {
                contentType.toString()
            } else {
                "${contentType}; charset=${clientConfiguration.charset.name()}"
            }
            addHeader(HttpHeaders.CONTENT_TYPE, valueWithEncoding)
            contentTypeForEntity = ContentType.parse(valueWithEncoding)
        }

        val completeUri = computeUri(clientConfiguration)
        val requestBuilder = AsyncRequestBuilder
            .create(method.toString())
            .setUri(completeUri)
        when {
            bodyString != null -> {
                if (contentTypeForEntity != null) {
                    requestBuilder.setEntity(bodyString.toString(), contentTypeForEntity)
                } else {
                    requestBuilder.setEntity(bodyString.toString())
                }
            }

            bodyBytes.isNotEmpty() -> {
                val ct = contentTypeForEntity ?: ContentType.APPLICATION_OCTET_STREAM
                requestBuilder.setEntity(bodyBytes, ct)
            }
        }
        return requestBuilder
            .setHeaders(*(headers.map { BasicHeader(it.key.toString(), it.value.toString()) }.toTypedArray()))
            .addParameters(*parameters)
            .build()
    }

}
