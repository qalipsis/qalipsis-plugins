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
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import org.apache.hc.client5.http.cookie.Cookie
import org.apache.hc.client5.http.entity.mime.ContentBody
import org.apache.hc.client5.http.entity.mime.FileBody
import org.apache.hc.client5.http.entity.mime.InputStreamBody
import org.apache.hc.client5.http.entity.mime.MultipartEntityBuilder
import org.apache.hc.client5.http.entity.mime.StringBody
import org.apache.hc.core5.http.ContentType
import org.apache.hc.core5.http.HttpHeaders.CONTENT_TYPE
import org.apache.hc.core5.http.message.BasicHeader
import org.apache.hc.core5.http.nio.AsyncEntityProducer
import org.apache.hc.core5.http.nio.AsyncRequestProducer
import org.apache.hc.core5.http.nio.entity.AsyncEntityProducers
import org.apache.hc.core5.http.nio.support.AsyncRequestBuilder

/**
 * HTTP Request for multipart/form-data submissions.
 *
 * @author Francisca Eze
 */
data class MultipartHttpRequest internal constructor(
    override val method: HttpMethod,
    override val uri: String
) : AbstractHttpRequest<MultipartHttpRequest>(),
    InternalHttpRequest<MultipartHttpRequest, org.apache.hc.core5.http.HttpRequest> {

    override val headers = mutableMapOf<CharSequence, Any>()

    override val parameters = mutableMapOf<CharSequence, MutableCollection<CharSequence>>()

    private val cookies = mutableMapOf<String, Cookie>()

    private val parts = mutableListOf<Part>()

    private val multipartBuilder = MultipartEntityBuilder.create()

    /**
     * Adds a simple text part.
     */
    fun addTextPart(
        name: String,
        value: String,
        contentType: ContentType = ContentType.TEXT_PLAIN.withCharset(Charsets.UTF_8)
    ): MultipartHttpRequest = apply {
        parts += Part(name, StringBody(value, contentType))
    }

    /**
     * Adds a file part (streamed from disk).
     */
    fun addFilePart(
        name: String,
        file: File,
        contentType: ContentType = ContentType.DEFAULT_BINARY
    ): MultipartHttpRequest = apply {
        parts += Part(name, FileBody(file, contentType, file.name))
    }

    /**
     * Adds a stream part (streamed; caller owns closing the stream).
     */
    fun addStreamPart(
        name: String,
        filename: String? = null,
        contentType: ContentType = ContentType.DEFAULT_BINARY,
        streamSupplier: () -> InputStream
    ): MultipartHttpRequest = apply {
        val streamBody = InputStreamBody(streamSupplier(), contentType, filename)
        parts += Part(name, streamBody)
    }

    /**
     * Sets the boundary string for the multipart request.
     */
    fun setBoundary(boundary: String): MultipartHttpRequest {
        multipartBuilder.setBoundary(boundary)
        return this
    }

    override fun addCookies(vararg cookie: Cookie): MultipartHttpRequest {
        cookie.forEach { cookies[it.name] = it }
        return this
    }

    override fun addHeader(name: CharSequence, value: Any): MultipartHttpRequest {
        headers[name] = value
        return this
    }

    override fun computeUri(clientConfiguration: HttpClientConfiguration): String {
        return getCompleteUri(clientConfiguration)
    }

    override fun toAsyncRequest(clientConfiguration: HttpClientConfiguration): AsyncRequestProducer {
        val builtEntity = buildAsyncEntity()
        // Set the correct Content-Type header including boundary.
        headers[CONTENT_TYPE] = builtEntity.contentTypeHeaderValue
        val completeUri = computeUri(clientConfiguration)
        val requestBuilder = AsyncRequestBuilder
            .create(method.toString())
            .setUri(completeUri)
            .setEntity(builtEntity.asyncEntityProducer)
            .setHeaders(*(headers.map { BasicHeader(it.key.toString(), it.value.toString()) }.toTypedArray()))
            .addParameters(*parameters.toUrlParams())
        if (cookies.isNotEmpty()) {
            val cookieHeader = cookies.values.joinToString("; ") { "${it.name}=${it.value}" }
            requestBuilder.addHeader(BasicHeader("Cookie", cookieHeader))
        }

        return requestBuilder.build()
    }


    /**
     * Builds the multipart entity as bytes for Async execution, but uses HttpComponents to generate
     * correct multipart formatting + boundary.
     */
    private fun buildAsyncEntity(): BuiltMultipartEntity {
        // Always use multipart/form-data; boundary is generated internally.
        multipartBuilder.setContentType(ContentType.MULTIPART_FORM_DATA)
        parts.forEach { part ->
            multipartBuilder.addPart(part.name, part.body)
        }
        val entity = multipartBuilder.build()

        // Ensure boundary + content-type are aligned: take content-type from the built entity.
        val contentType = entity.contentType
        val byteArrayOutputStream = ByteArrayOutputStream()
        entity.writeTo(byteArrayOutputStream)
        val bytes = byteArrayOutputStream.toByteArray()

        return BuiltMultipartEntity(
            asyncEntityProducer = AsyncEntityProducers.create(bytes, ContentType.parse(contentType)),
            contentTypeHeaderValue = contentType
        )
    }
}

private data class Part(val name: String, val body: ContentBody)

data class BuiltMultipartEntity(
    val asyncEntityProducer: AsyncEntityProducer,
    val contentTypeHeaderValue: String
)