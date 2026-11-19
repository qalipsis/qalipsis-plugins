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
import java.net.URLEncoder
import java.nio.charset.Charset
import org.apache.hc.client5.http.cookie.Cookie
import org.apache.hc.core5.http.ContentType
import org.apache.hc.core5.http.HttpHeaders.CONTENT_TYPE
import org.apache.hc.core5.http.message.BasicHeader
import org.apache.hc.core5.http.nio.AsyncRequestProducer
import org.apache.hc.core5.http.nio.entity.StringAsyncEntityProducer
import org.apache.hc.core5.http.nio.support.AsyncRequestBuilder

/**
 * HTTP Request for application/x-www-form-urlencoded submissions.
 *
 * @author Francisca Eze
 */
data class FormHttpRequest internal constructor(
    override val method: HttpMethod,
    override val uri: String
) : AbstractHttpRequest<FormHttpRequest>(),
    InternalHttpRequest<FormHttpRequest, org.apache.hc.core5.http.HttpRequest> {

    override val headers = mutableMapOf<CharSequence, Any>()
    override val parameters = mutableMapOf<CharSequence, MutableCollection<CharSequence>>()
    private val cookies = mutableMapOf<String, Cookie>()

    @KTestable
    private val formFields = mutableMapOf<CharSequence, MutableCollection<CharSequence>>()

    /**
     * Adds a form field with a single value.
     */
    fun addFormField(name: CharSequence, value: CharSequence): FormHttpRequest {
        formFields[name] = mutableListOf(value)
        return this
    }

    /**
     * Adds multiple values for a form field.
     */
    fun addFormFields(name: CharSequence, vararg values: CharSequence): FormHttpRequest {
        formFields[name] =  mutableListOf(*values)
        return this
    }

    override fun addCookies(vararg cookie: Cookie): FormHttpRequest {
        cookie.forEach { cookies[it.name] = it }
        return this
    }

    override fun addHeader(name: CharSequence, value: Any): FormHttpRequest {
        headers[name.toString().lowercase()] = value
        return this
    }

    override fun computeUri(clientConfiguration: HttpClientConfiguration): String {
        return getCompleteUri(clientConfiguration)
    }

    override fun toAsyncRequest(clientConfiguration: HttpClientConfiguration): AsyncRequestProducer {
        val completeUri = computeUri(clientConfiguration)
        val builder = AsyncRequestBuilder
            .create(method.toString())
            .setUri(completeUri)
            .addParameters(*parameters.toUrlParams())

        val contentType = ContentType.APPLICATION_FORM_URLENCODED.withCharset(clientConfiguration.charset)
        headers[CONTENT_TYPE] = "${contentType.mimeType}; charset=${clientConfiguration.charset.name()}"

        headers.forEach { (k, v) ->
            builder.addHeader(BasicHeader(k.toString(), v.toString()))
        }

        // Apply cookies as a Cookie header if provided.
        if (cookies.isNotEmpty()) {
            val cookieHeader = cookies.values.joinToString("; ") { "${it.name}=${it.value}" }
            builder.addHeader(BasicHeader("Cookie", cookieHeader))
        }

        // Only attach a body for methods that allow one.
        if (allowsBody(method)) {
            builder.setEntity(buildFormBody(clientConfiguration.charset))
        }

        return builder.build()
    }

    private fun buildFormBody(charset: Charset): StringAsyncEntityProducer {
        val encoded = encodeFormFields(charset)
        return StringAsyncEntityProducer(
            encoded,
            ContentType.APPLICATION_FORM_URLENCODED.withCharset(charset)
        )
    }

    private fun encodeFormFields(charset: Charset): String {
        return formFields.entries.joinToString("&") { (key, values) ->
            values.joinToString("&") { value ->
                "${urlEncode(key, charset)}=${urlEncode(value, charset)}"
            }
        }
    }

    // For x-www-form-urlencoded, spaces must be encoded as '+'.
    private fun urlEncode(input: CharSequence, charset: Charset): String =
        URLEncoder.encode(input.toString(), charset.name())

    private fun allowsBody(method: HttpMethod): Boolean =
        when (method) {
            HttpMethod.POST, HttpMethod.PUT, HttpMethod.PATCH, HttpMethod.DELETE -> true
            else -> false
        }

}
