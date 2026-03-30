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
