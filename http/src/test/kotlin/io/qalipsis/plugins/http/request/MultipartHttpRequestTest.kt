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

import io.mockk.every
import io.mockk.mockk
import io.qalipsis.plugins.http.HttpClientConfiguration
import java.nio.charset.StandardCharsets
import kotlin.io.path.createTempFile
import org.apache.hc.client5.http.impl.cookie.BasicClientCookie
import org.apache.hc.core5.http.HttpHeaders
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * @author Francisca Eze
 */
internal class MultipartHttpRequestTest {

    private val clientConfiguration = mockk<HttpClientConfiguration> {
        every { charset } returns StandardCharsets.UTF_8
        every { scheme } returns "http"
    }

    @Test
    fun `should create basic multipart POST request`() {
        val request = MultipartHttpRequest(
            HttpMethod.POST,
            "http://localhost:8000/upload"
        )

        request.toAsyncRequest(clientConfiguration)

        assertEquals(HttpMethod.POST, request.method)
        assertEquals("http://localhost:8000/upload", request.uri)
    }

    @Test
    fun `should add text part`() {
        val request = MultipartHttpRequest(HttpMethod.POST, "http://localhost")
            .addTextPart("username", "john")

        request.toAsyncRequest(clientConfiguration)

        // we can only verify indirectly: content type must be multipart
        assertTrue(request.headers.containsKey(HttpHeaders.CONTENT_TYPE))
    }

    @Test
    fun `should add file part without throwing`() {
        val file = createTempFile().toFile().apply {
            writeText("hello-file")
        }

        val request = MultipartHttpRequest(HttpMethod.POST, "http://localhost")
            .addFilePart("file", file)

        request.toAsyncRequest(clientConfiguration)

        assertTrue(request.headers.containsKey(HttpHeaders.CONTENT_TYPE))
    }

    @Test
    fun `should add stream part`() {
        val request = MultipartHttpRequest(HttpMethod.POST, "http://localhost")
            .addStreamPart("data", "data.txt") {
                "stream-content".byteInputStream()
            }

        request.toAsyncRequest(clientConfiguration)

        assertTrue(request.headers.containsKey(HttpHeaders.CONTENT_TYPE))
    }

    @Test
    fun `should attach query parameters`() {
        val request = MultipartHttpRequest(HttpMethod.POST, "http://localhost")
            .apply {
                parameters["debug"] = mutableListOf("true")
                parameters["v"] = mutableListOf("2")
            }

        request.toAsyncRequest(clientConfiguration)

        assertEquals(2, request.parameters.size)
        assertTrue(request.parameters["debug"]!!.contains("true"))
        assertTrue(request.parameters["v"]!!.contains("2"))
    }

    @Test
    fun `should attach custom headers`() {
        val request = MultipartHttpRequest(HttpMethod.POST, "http://localhost")
            .addHeader("X-Test", "123")

        request.toAsyncRequest(clientConfiguration)

        assertEquals("123", request.headers["X-Test"])
    }

    @Test
    fun `should attach cookies`() {
        val cookie = BasicClientCookie("session", "abc123")

        val request = MultipartHttpRequest(HttpMethod.POST, "http://localhost")
            .addCookies(cookie)

        request.toAsyncRequest(clientConfiguration)

        assertTrue(request.headers.containsKey(HttpHeaders.CONTENT_TYPE))
    }

    @Test
    fun `should set multipart content type including boundary`() {
        val request = MultipartHttpRequest(HttpMethod.POST, "http://localhost")
            .addTextPart("a", "b")

        request.toAsyncRequest(clientConfiguration)

        val contentType = request.headers[HttpHeaders.CONTENT_TYPE].toString()

        assertTrue(contentType.startsWith("multipart/form-data"))
        assertTrue(contentType.contains("boundary="))
    }

    @Test
    fun `should allow custom boundary`() {
        val boundary = "CUSTOM_BOUNDARY"

        val request = MultipartHttpRequest(HttpMethod.POST, "http://localhost")
            .setBoundary(boundary)
            .addTextPart("k", "v")

        request.toAsyncRequest(clientConfiguration)

        val contentType = request.headers[HttpHeaders.CONTENT_TYPE].toString()

        assertTrue(contentType.contains(boundary))
    }

    @Test
    fun `should handle empty multipart request`() {
        val request = MultipartHttpRequest(HttpMethod.POST, "http://localhost")

        request.toAsyncRequest(clientConfiguration)

        val contentType = request.headers[HttpHeaders.CONTENT_TYPE].toString()

        assertTrue(contentType.startsWith("multipart/form-data"))
    }
}
