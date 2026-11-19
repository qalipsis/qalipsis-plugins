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

import assertk.assertThat
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import io.qalipsis.plugins.http.HttpClientConfiguration
import io.qalipsis.plugins.http.HttpClientStepSpecificationImpl
import io.qalipsis.plugins.http.request.catadioptre.bodyString
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.steps.StepTestHelper
import java.nio.charset.StandardCharsets
import org.apache.hc.core5.http.ContentType
import org.apache.hc.core5.http.HttpHeaders
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension


internal class SimpleHttpRequestTest {

    @JvmField
    @RegisterExtension
    val testDispatcherProvider = TestDispatcherProvider()

    private val clientConfiguration = mockk<HttpClientConfiguration> {
        every { charset } returns StandardCharsets.UTF_8
        every { scheme } returns "http"
    }

    @Test
    fun `should parse basic HTTP request specification with no body to a valid request producer`() =
        testDispatcherProvider.run {
            //given
            val stepSpec = HttpClientStepSpecificationImpl<Any, Any>()
            val requestBuilder = spyk<HttpRequestBuilder>()
            val ctx = StepTestHelper.createStepContext<String, String>(
                input = "This is a test",
                minionId = "client-1"
            )
            stepSpec.apply {
                request { _, _ ->
                    simple(HttpMethod.GET, "http://localhost:8000")
                        .addHeader("Arbitrary", "Header")
                        .contentType(ContentType.TEXT_PLAIN)
                }
            }
            val request = stepSpec.requestFactory.invoke(requestBuilder, ctx, "") as SimpleHttpRequest

            //when
            val producer = request.toAsyncRequest(clientConfiguration)

            //then
            assertNotNull(producer)
            assertEquals(HttpMethod.GET, request.method)
            assertEquals(request.uri, "http://localhost:8000")
            assertNull(request.bodyString())
            assertEquals(request.headers["Arbitrary"], "Header")
            assertNotNull(request.headers["Content-Type"])
            assertTrue(request.headers["Content-Type"].toString().contains("text/plain"))
        }

    @Test
    fun `should parse POST request with JSON body to valid request producer`() = testDispatcherProvider.run {
        //given
        val stepSpec = HttpClientStepSpecificationImpl<Any, Any>()
        val requestBuilder = spyk<HttpRequestBuilder>()
        val ctx = StepTestHelper.createStepContext<String, String>(
            input = "This is a test",
            minionId = "client-1"
        )
        stepSpec.apply {
            request { _, _ ->
                simple(HttpMethod.POST, "http://localhost:8000/api/users")
                    .body("""{"name":"John","email":"john@example.com"}""", ContentType.APPLICATION_JSON)
            }
        }
        val request = stepSpec.requestFactory.invoke(requestBuilder, ctx, "") as SimpleHttpRequest

        //when
        val producer = request.toAsyncRequest(clientConfiguration)

        //then
        assertNotNull(producer)
        assertEquals(request.method, HttpMethod.POST)
        assertEquals(request.uri, "http://localhost:8000/api/users")
        assertThat(
            request.bodyString().contentEquals(
                """
                    {"name":"John","email":"john@example.com"}
                """.trimIndent()
            )
        )
        assertNotNull(request.headers[HttpHeaders.CONTENT_TYPE])
        assertTrue(request.headers[HttpHeaders.CONTENT_TYPE].toString().contains("application/json"))
    }

    @Test
    fun `should parse POST request with JSON body and authentication to valid request producer`() =
        testDispatcherProvider.run {
            //given
            val stepSpec = HttpClientStepSpecificationImpl<Any, Any>()
            val requestBuilder = spyk<HttpRequestBuilder>()
            val ctx = StepTestHelper.createStepContext<String, String>(
                input = "This is a test",
                minionId = "client-1"
            )
            stepSpec.apply {
                request { _, _ ->
                    simple(HttpMethod.POST, "http://localhost:8000/api/users")
                        .body("""{"name":"John","email":"john@example.com"}""", ContentType.APPLICATION_JSON)
                        .withBasicAuth("admin", "password123")
                }
            }
            val request = stepSpec.requestFactory.invoke(requestBuilder, ctx, "") as SimpleHttpRequest

            //when
            val producer = request.toAsyncRequest(clientConfiguration)

            //then
            assertNotNull(producer)
            assertEquals(request.method, HttpMethod.POST)
            assertEquals(request.uri, "http://localhost:8000/api/users")
            assertNotNull(request.headers[HttpHeaders.AUTHORIZATION])
            assertTrue(request.headers[HttpHeaders.AUTHORIZATION].toString().contains("Basic"))
        }

    @Test
    fun `should parse PUT request with string body to valid request producer`() = testDispatcherProvider.run {
        //given
        val stepSpec = HttpClientStepSpecificationImpl<Any, Any>()
        val requestBuilder = spyk<HttpRequestBuilder>()
        val ctx = StepTestHelper.createStepContext<String, String>(
            input = "This is a test",
            minionId = "client-1"
        )
        stepSpec.apply {
            request { _, _ ->
                simple(HttpMethod.PUT, "http://localhost:8000/api/resource/123")
                    .body("Updated content")
                    .contentType(ContentType.TEXT_PLAIN)
            }
        }
        val request = stepSpec.requestFactory.invoke(requestBuilder, ctx, "") as SimpleHttpRequest

        //when
        val producer = request.toAsyncRequest(clientConfiguration)

        //then
        assertNotNull(producer)
        assertEquals( request.method, HttpMethod.PUT)
        assertEquals("Updated content", request.bodyString())
        assertEquals(request.uri, "http://localhost:8000/api/resource/123")
    }

    @Test
    fun `should parse DELETE request with query parameters to valid request producer`() = testDispatcherProvider.run {
        //given
        val stepSpec = HttpClientStepSpecificationImpl<Any, Any>()
        val requestBuilder = spyk<HttpRequestBuilder>()
        val ctx = StepTestHelper.createStepContext<String, String>(
            input = "This is a test",
            minionId = "client-1"
        )
        stepSpec.apply {
            request { _, _ ->
                simple(HttpMethod.DELETE, "http://localhost:8000/api/users/123")
                    .addParameter("force", "true")
                    .addParameter("cascade", "false")
                    .addHeader("Authorization", "Bearer token123")
            }
        }
        val request = stepSpec.requestFactory.invoke(requestBuilder, ctx, "") as SimpleHttpRequest

        //when
        val producer = request.toAsyncRequest(clientConfiguration)

        //then
        assertNotNull(producer)
        assertEquals(request.method, HttpMethod.DELETE)
        assertEquals(request.uri, "http://localhost:8000/api/users/123")
        assertEquals(request.parameters.size, 2)
        assertTrue(request.parameters["force"]?.contains("true") == true)
        assertTrue(request.parameters["cascade"]?.contains("false") == true)
    }

    @Test
    fun `should parse request with multiple parameter values to valid request producer`() = testDispatcherProvider.run {
        //given
        val stepSpec = HttpClientStepSpecificationImpl<Any, Any>()
        val requestBuilder = spyk<HttpRequestBuilder>()
        val ctx = StepTestHelper.createStepContext<String, String>(
            input = "This is a test",
            minionId = "client-1"
        )
        stepSpec.apply {
            request { _, _ ->
                simple(HttpMethod.GET, "http://localhost:8000/search")
                    .addParameter("tags", "kotlin")
                    .addParameter("tags", "java")
                    .addParameter("tags", "http")
                    .addParameter("limit", "10")
            }
        }
        val request = stepSpec.requestFactory.invoke(requestBuilder, ctx, "") as SimpleHttpRequest

        //when
        val producer = request.toAsyncRequest(clientConfiguration)

        //then
        assertNotNull(producer)
        val tagsValues = request.parameters["tags"]!!
        assertEquals(tagsValues.size, 3)
        assertTrue(tagsValues.containsAll(listOf("kotlin", "java", "http")))
        assertEquals(request.parameters["limit"], listOf("10"))
    }

    @Test
    fun `should parse PATCH request with binary body to valid request producer`() = testDispatcherProvider.run {
        //given
        val stepSpec = HttpClientStepSpecificationImpl<Any, Any>()
        val requestBuilder = spyk<HttpRequestBuilder>()
        val ctx = StepTestHelper.createStepContext<String, String>(
            input = "This is a test",
            minionId = "client-1"
        )
        val binaryData = byteArrayOf(0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
        stepSpec.apply {
            request { _, _ ->
                simple(HttpMethod.PATCH, "http://localhost:8000/api/images/123")
                    .body(binaryData, ContentType.IMAGE_PNG.toString())
                    .addHeader("X-File-Name", "image.png")
            }
        }
        val request = stepSpec.requestFactory.invoke(requestBuilder, ctx, "") as SimpleHttpRequest

        //when
        val producer = request.toAsyncRequest(clientConfiguration)

        //then
        assertNotNull(producer)
        assertEquals(request.method, HttpMethod.PATCH)
        assertNotNull(request.headers[HttpHeaders.CONTENT_TYPE])
        assertTrue(request.headers[HttpHeaders.CONTENT_TYPE].toString().contains("image/png"))
    }

    @Test
    fun `should parse HEAD request to valid request producer`() = testDispatcherProvider.run {
        //given
        val stepSpec = HttpClientStepSpecificationImpl<Any, Any>()
        val requestBuilder = spyk<HttpRequestBuilder>()
        val ctx = StepTestHelper.createStepContext<String, String>(
            input = "This is a test",
            minionId = "client-1"
        )
        stepSpec.apply {
            request { _, _ ->
                simple(HttpMethod.HEAD, "http://localhost:8000/api/resource")
                    .addHeader("Accept", "*/*")
            }
        }
        val request = stepSpec.requestFactory.invoke(requestBuilder, ctx, "") as SimpleHttpRequest

        //when
        val producer = request.toAsyncRequest(clientConfiguration)

        //then
        assertNotNull(producer)
        assertEquals(request.method, HttpMethod.HEAD)
        assertEquals(request.headers["Accept"], "*/*")
    }

    @Test
    fun `should parse OPTIONS request to valid request producer`() = testDispatcherProvider.run {
        //given
        val stepSpec = HttpClientStepSpecificationImpl<Any, Any>()
        val requestBuilder = spyk<HttpRequestBuilder>()
        val ctx = StepTestHelper.createStepContext<String, String>(
            input = "This is a test",
            minionId = "client-1"
        )
        stepSpec.apply {
            request { _, _ ->
                simple(HttpMethod.OPTIONS, "http://localhost:8000/api")
                    .addHeader("Access-Control-Request-Method", "POST")
            }
        }
        val request = stepSpec.requestFactory.invoke(requestBuilder, ctx, "") as SimpleHttpRequest

        //when
        val producer = request.toAsyncRequest(clientConfiguration)

        //then
        assertNotNull(producer)
        assertEquals(HttpMethod.OPTIONS, request.method)
    }

    @Test
    fun `should parse request with multiple headers to valid request producer`() = testDispatcherProvider.run {
        //given
        val stepSpec = HttpClientStepSpecificationImpl<Any, Any>()
        val requestBuilder = spyk<HttpRequestBuilder>()
        val ctx = StepTestHelper.createStepContext<String, String>(
            input = "This is a test",
            minionId = "client-1"
        )
        stepSpec.apply {
            request { _, _ ->
                simple(HttpMethod.GET, "http://localhost:8000")
                    .addHeader("X-Custom-Header-1", "value1")
                    .addHeader("X-Custom-Header-2", "value2")
                    .addHeader("Authorization", "Bearer token")
                    .addHeader("Accept", "application/json")
            }
        }
        val request = stepSpec.requestFactory.invoke(requestBuilder, ctx, "") as SimpleHttpRequest

        //when
        val producer = request.toAsyncRequest(clientConfiguration)

        //then
        assertNotNull(producer)
        assertEquals(request.headers["X-Custom-Header-1"], "value1")
        assertEquals(request.headers["X-Custom-Header-2"], "value2")
        assertEquals(request.headers["Authorization"], "Bearer token")
        assertEquals(request.headers["Accept"], "application/json")
    }

    @Test
    fun `should parse request with UTF-8 content to valid request producer`() = testDispatcherProvider.run {
        //given
        val stepSpec = HttpClientStepSpecificationImpl<Any, Any>()
        val requestBuilder = spyk<HttpRequestBuilder>()
        val ctx = StepTestHelper.createStepContext<String, String>(
            input = "This is a test",
            minionId = "client-1"
        )
        stepSpec.apply {
            request { _, _ ->
                simple(HttpMethod.POST, "http://localhost:8000/api/messages")
                    .body("Hello 🌍", ContentType.TEXT_PLAIN)
            }
        }
        val request = stepSpec.requestFactory.invoke(requestBuilder, ctx, "") as SimpleHttpRequest

        //when
        val producer = request.toAsyncRequest(clientConfiguration)

        //then
        assertNotNull(producer)
    }

    @Test
    fun `should parse request with special characters in headers to valid request producer`() =
        testDispatcherProvider.run {
            //given
            val stepSpec = HttpClientStepSpecificationImpl<Any, Any>()
            val requestBuilder = spyk<HttpRequestBuilder>()
            val ctx = StepTestHelper.createStepContext<String, String>(
                input = "This is a test",
                minionId = "client-1"
            )
            stepSpec.apply {
                request { _, _ ->
                    simple(HttpMethod.GET, "http://localhost:8000")
                        .addHeader("X-Special", "value with spaces & symbols!")
                }
            }
            val request = stepSpec.requestFactory.invoke(requestBuilder, ctx, "") as SimpleHttpRequest

            //when
            val producer = request.toAsyncRequest(clientConfiguration)

            //then
            assertNotNull(producer)
            assertEquals(request.headers["X-Special"], "value with spaces & symbols!")
        }

    @Test
    fun `should parse request with special characters in parameters to valid request producer`() =
        testDispatcherProvider.run {
            //given
            val stepSpec = HttpClientStepSpecificationImpl<Any, Any>()
            val requestBuilder = spyk<HttpRequestBuilder>()
            val ctx = StepTestHelper.createStepContext<String, String>(
                input = "This is a test",
                minionId = "client-1"
            )
            stepSpec.apply {
                request { _, _ ->
                    simple(HttpMethod.GET, "http://localhost:8000/search")
                        .addParameter("query", "search term with spaces")
                }
            }
            val request = stepSpec.requestFactory.invoke(requestBuilder, ctx, "") as SimpleHttpRequest

            //when
            val producer = request.toAsyncRequest(clientConfiguration)

            //then
            assertNotNull(producer)
            assertTrue(request.parameters["query"]?.contains("search term with spaces") == true)
        }

    @Test
    fun `should parse request with charset in content type to valid request producer`() = testDispatcherProvider.run {
        //given
        val stepSpec = HttpClientStepSpecificationImpl<Any, Any>()
        val requestBuilder = spyk<HttpRequestBuilder>()
        val ctx = StepTestHelper.createStepContext<String, String>(
            input = "This is a test",
            minionId = "client-1"
        )
        stepSpec.apply {
            request { _, _ ->
                simple(HttpMethod.POST, "http://localhost:8000/api/data")
                    .body("test content", ContentType.APPLICATION_JSON.withCharset(StandardCharsets.UTF_8))
            }
        }
        val request = stepSpec.requestFactory.invoke(requestBuilder, ctx, "") as SimpleHttpRequest

        //when
        val producer = request.toAsyncRequest(clientConfiguration)

        //then
        assertNotNull(producer)
        val contentType = request.headers[HttpHeaders.CONTENT_TYPE].toString()
        assertTrue(contentType.contains("application/json"))
        assertTrue(contentType.contains("UTF-8"))
    }

    @Test
    fun `should parse request with body replacement to valid request producer`() = testDispatcherProvider.run {
        //given
        val stepSpec = HttpClientStepSpecificationImpl<Any, Any>()
        val requestBuilder = spyk<HttpRequestBuilder>()
        val ctx = StepTestHelper.createStepContext<String, String>(
            input = "This is a test",
            minionId = "client-1"
        )
        stepSpec.apply {
            request { _, _ ->
                simple(HttpMethod.POST, "http://localhost:8000/api/data")
                    .body("first body")
                    .body("second body", ContentType.TEXT_PLAIN)
            }
        }
        val request = stepSpec.requestFactory.invoke(requestBuilder, ctx, "") as SimpleHttpRequest

        //when
        val producer = request.toAsyncRequest(clientConfiguration)

        //then
        assertNotNull(producer)
    }

    @Test
    fun `should parse complex request with all components to valid request producer`() = testDispatcherProvider.run {
        //given
        val stepSpec = HttpClientStepSpecificationImpl<Any, Any>()
        val requestBuilder = spyk<HttpRequestBuilder>()
        val ctx = StepTestHelper.createStepContext<String, String>(
            input = "This is a test",
            minionId = "client-1"
        )
        stepSpec.apply {
            request { _, _ ->
                simple(HttpMethod.POST, "http://localhost:8000/api/search")
                    .addHeader("Authorization", "Bearer token123")
                    .addHeader("Accept", "application/json")
                    .addHeader("X-API-Version", "v2")
                    .addParameter("format", "json")
                    .addParameter("debug", "true")
                    .body("""{"query":"test","filters":{"status":"active"}}""", ContentType.APPLICATION_JSON)
            }
        }
        val request = stepSpec.requestFactory.invoke(requestBuilder, ctx, "") as SimpleHttpRequest

        //when
        val producer = request.toAsyncRequest(clientConfiguration)

        //then
        assertNotNull(producer)
        assertEquals(request.method, HttpMethod.POST)
        assertEquals( request.headers["Authorization"], "Bearer token123")
        assertEquals( request.headers["Accept"], "application/json")
        assertEquals(request.headers["X-API-Version"], "v2")
        assertEquals(request.parameters.size, 2)
        assertTrue(request.parameters["format"]?.contains("json") == true)
        assertTrue(request.parameters["debug"]?.contains("true") == true)
    }

}

