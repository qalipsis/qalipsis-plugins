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

package io.qalipsis.plugins.http.response

import io.qalipsis.api.logging.LoggerHelper.logger
import kotlin.reflect.KClass
import org.apache.hc.client5.http.async.methods.SimpleHttpResponse
import org.apache.hc.client5.http.protocol.HttpClientContext

/**
 * Converter of the HTTP response to the expected type.
 *
 * @author Francisca Eze
 */
internal class ResponseConverter<B>(
    private val argumentType: KClass<*>,
    private val deserializers: List<HttpBodyDeserializer>
) {

    fun convert(response: SimpleHttpResponse, httpClientContext: HttpClientContext): HttpResponse<B> {
        return (response).let {
            val headers = response.headers?.associate { it.name to it.value } ?: emptyMap()
            val responseType = headers.filterKeys { it.lowercase() == CONTENT_TYPE_HEADER }.values.firstOrNull()
                ?.let { MediaType(it) }
            val usedContentType = responseType ?: MediaType.TEXT_PLAIN_TYPE
            val cookieStore = httpClientContext.cookieStore
            val customCookies = cookieStore.cookies.associateBy { cookie -> cookie.name }
            val bytes: ByteArray? = response.bodyBytes
            val body = bytes?.let { convertBody(response, bytes, usedContentType) }
            DefaultHttpResponse(
                code = response.code,
                bodyBytes = bytes,
                headers = response.headers?.associate { it.name to it.value } ?: emptyMap(),
                reason = response.reasonPhrase ?: "",
                contentType = response.contentType,
                cookies = customCookies,
                body = body
            )
        }
    }

    /**
     * Converts the body to the expected type if the response is successful, null otherwise.
     */
    @Suppress("UNCHECKED_CAST", "IMPLICIT_CAST_TO_ANY")
    private fun convertBody(
        response: SimpleHttpResponse,
        bytes: ByteArray,
        usedContentType: MediaType
    ) = if (response.code in 200..299) {
        when (argumentType) {
            Unit::class -> Unit
            String::class -> String(bytes, usedContentType.charset)
            ByteArray::class -> bytes
            else -> deserialize(bytes, usedContentType)
        }
    } else {
        log.trace { "Body converted to null because of non successful HTTP status" }
        null
    } as B?

    @Suppress("UNCHECKED_CAST")
    private fun deserialize(bytes: ByteArray, responseType: MediaType): B? {
        log.trace { "Search the converters for the media type $responseType" }
        return deserializers
            // All the matching converters are tried until one is working.
            .asSequence()
            .filter { it.accept(responseType) }
            .mapNotNull { deserializer ->
                kotlin.runCatching {
                    log.trace { "Trying to convert the response body with the deserializer ${deserializer::class}" }
                    deserializer.convert(bytes, responseType, argumentType).apply {
                        log.trace { "Response body successfully converted with the deserializer ${deserializer::class}" }
                    }
                }.getOrNull()
            }
            .firstOrNull() as B?
    }

    companion object {

        private const val CONTENT_TYPE_HEADER = "content-type"

        private val log = logger()

    }
}
