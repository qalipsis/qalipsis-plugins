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

import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import io.qalipsis.plugins.http.HttpClientConfiguration
import io.qalipsis.plugins.http.HttpClientStepSpecificationImpl
import io.qalipsis.plugins.http.request.catadioptre.formFields
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.steps.StepTestHelper
import java.nio.charset.StandardCharsets
import org.apache.hc.core5.http.HttpHeaders
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

internal class FormHttpRequestTest {

    @JvmField
    @RegisterExtension
    val testDispatcherProvider = TestDispatcherProvider()

    private val clientConfiguration = mockk<HttpClientConfiguration> {
        every { charset } returns StandardCharsets.UTF_8
        every { scheme } returns "http"
    }

    @Test
    fun `should parse basic POST form request`() = testDispatcherProvider.run {
        val stepSpec = HttpClientStepSpecificationImpl<Any, Any>()
        val requestBuilder = spyk<HttpRequestBuilder>()
        val ctx = StepTestHelper.createStepContext<String, String>(
            input = "input",
            minionId = "client-1"
        )

        stepSpec.apply {
            request { _, _ ->
                form(HttpMethod.POST, "http://localhost:8000/api/form")
                    .addFormField("username", "john_doe")
                    .addFormField("email", "john@example.com")
            }
        }

        val request = stepSpec.requestFactory.invoke(requestBuilder, ctx, "") as FormHttpRequest
        request.toAsyncRequest(clientConfiguration)

        assertEquals(HttpMethod.POST, request.method)
        assertEquals("http://localhost:8000/api/form", request.uri)
        assertEquals(2, request.formFields().size)
        assertEquals(listOf("john_doe"), request.formFields()["username"])
        assertEquals(listOf("john@example.com"), request.formFields()["email"])
    }

    @Test
    fun `should preserve UTF-8 characters in form fields`() = testDispatcherProvider.run {
        val stepSpec = HttpClientStepSpecificationImpl<Any, Any>()
        val requestBuilder = spyk<HttpRequestBuilder>()
        val ctx = StepTestHelper.createStepContext<String, String>(input = "input", minionId = "client-1")

        stepSpec.apply {
            request { _, _ ->
                form(HttpMethod.POST, "http://localhost:8000/form")
                    .addFormField("name", "José García")
                    .addFormField("message", "Hello 🌍 世界")
            }
        }

        val request = stepSpec.requestFactory.invoke(requestBuilder, ctx, "") as FormHttpRequest
        request.toAsyncRequest(clientConfiguration)

        assertEquals(listOf("José García"), request.formFields()["name"])
        assertEquals(listOf("Hello 🌍 世界"), request.formFields()["message"])
    }

    @Test
    fun `should support multiple form fields`() = testDispatcherProvider.run {
        val stepSpec = HttpClientStepSpecificationImpl<Any, Any>()
        val requestBuilder = spyk<HttpRequestBuilder>()
        val ctx = StepTestHelper.createStepContext<String, String>(input = "input", minionId = "client-1")

        stepSpec.apply {
            request { _, _ ->
                form(HttpMethod.POST, "http://localhost:8000/register")
                    .addFormField("firstName", "John")
                    .addFormField("lastName", "Doe")
                    .addFormField("age", "30")
            }
        }

        val request = stepSpec.requestFactory.invoke(requestBuilder, ctx, "") as FormHttpRequest
        request.toAsyncRequest(clientConfiguration)

        assertEquals(3, request.formFields().size)
        assertEquals(listOf("John"), request.formFields()["firstName"])
        assertEquals(listOf("Doe"), request.formFields()["lastName"])
    }

    @Test
    fun `should override form field when same key is added twice`() = testDispatcherProvider.run {
        val stepSpec = HttpClientStepSpecificationImpl<Any, Any>()
        val requestBuilder = spyk<HttpRequestBuilder>()
        val ctx = StepTestHelper.createStepContext<String, String>(input = "input", minionId = "client-1")

        stepSpec.apply {
            request { _, _ ->
                form(HttpMethod.POST, "http://localhost:8000/form")
                    .addFormField("key", "original")
                    .addFormField("key", "replacement")
            }
        }

        val request = stepSpec.requestFactory.invoke(requestBuilder, ctx, "") as FormHttpRequest
        request.toAsyncRequest(clientConfiguration)

        assertEquals(1, request.formFields().size)
        assertEquals(listOf("replacement"), request.formFields()["key"])
    }

    @Test
    fun `should attach query parameters`() = testDispatcherProvider.run {
        val stepSpec = HttpClientStepSpecificationImpl<Any, Any>()
        val requestBuilder = spyk<HttpRequestBuilder>()
        val ctx = StepTestHelper.createStepContext<String, String>(input = "input", minionId = "client-1")

        stepSpec.apply {
            request { _, _ ->
                form(HttpMethod.POST, "http://localhost:8000/form")
                    .addFormField("data", "value")
                    .addParameter("debug", "true")
                    .addParameter("version", "v2")
            }
        }

        val request = stepSpec.requestFactory.invoke(requestBuilder, ctx, "") as FormHttpRequest
        request.toAsyncRequest(clientConfiguration)

        assertEquals(2, request.parameters.size)
        assertTrue(request.parameters["debug"]!!.contains("true"))
        assertTrue(request.parameters["version"]!!.contains("v2"))
    }

    @Test
    fun `should attach custom headers`() = testDispatcherProvider.run {
        val stepSpec = HttpClientStepSpecificationImpl<Any, Any>()
        val requestBuilder = spyk<HttpRequestBuilder>()
        val ctx = StepTestHelper.createStepContext<String, String>(input = "input", minionId = "client-1")

        stepSpec.apply {
            request { _, _ ->
                form(HttpMethod.POST, "http://localhost:8000/form")
                    .addFormField("username", "testuser")
                    .addHeader("X-Custom-Header", "customValue")
            }
        }

        val request = stepSpec.requestFactory.invoke(requestBuilder, ctx, "") as FormHttpRequest
        request.toAsyncRequest(clientConfiguration)

        assertTrue(request.headers.keys.any { it == "x-custom-header" })
    }

    @Test
    fun `should apply basic authentication header`() = testDispatcherProvider.run {
        val stepSpec = HttpClientStepSpecificationImpl<Any, Any>()
        val requestBuilder = spyk<HttpRequestBuilder>()
        val ctx = StepTestHelper.createStepContext<String, String>(input = "input", minionId = "client-1")

        stepSpec.apply {
            request { _, _ ->
                form(HttpMethod.POST, "http://localhost:8000/api/submit")
                    .addFormField("data", "test")
                    .withBasicAuth("admin", "password")
            }
        }

        val request = stepSpec.requestFactory.invoke(requestBuilder, ctx, "") as FormHttpRequest
        request.toAsyncRequest(clientConfiguration)

        val auth = request.headers[HttpHeaders.AUTHORIZATION]?.toString()
        assertNotNull(auth)
        assertTrue(auth!!.startsWith("Basic "))
        assertTrue(auth.length > "Basic ".length)
    }

    @Test
    fun `should handle empty form`() = testDispatcherProvider.run {
        val stepSpec = HttpClientStepSpecificationImpl<Any, Any>()
        val requestBuilder = spyk<HttpRequestBuilder>()
        val ctx = StepTestHelper.createStepContext<String, String>(input = "input", minionId = "client-1")

        stepSpec.apply {
            request { _, _ ->
                form(HttpMethod.POST, "http://localhost:8000/form")
            }
        }

        val request = stepSpec.requestFactory.invoke(requestBuilder, ctx, "") as FormHttpRequest
        request.toAsyncRequest(clientConfiguration)

        assertTrue(request.formFields().isEmpty())
    }

    @Test
    fun `should set correct content type including charset`() = testDispatcherProvider.run {
        val stepSpec = HttpClientStepSpecificationImpl<Any, Any>()
        val requestBuilder = spyk<HttpRequestBuilder>()
        val ctx = StepTestHelper.createStepContext<String, String>(input = "input", minionId = "client-1")

        stepSpec.apply {
            request { _, _ ->
                form(HttpMethod.POST, "http://localhost:8000/form")
                    .addFormField("username", "testuser")
            }
        }

        val request = stepSpec.requestFactory.invoke(requestBuilder, ctx, "") as FormHttpRequest
        request.toAsyncRequest(clientConfiguration)
        val contentType = request.headers[HttpHeaders.CONTENT_TYPE]?.toString()
        assertEquals(
            "application/x-www-form-urlencoded; charset=UTF-8",
            contentType
        )
    }

    @Test
    fun `should add multiple values using addFormFields`() = testDispatcherProvider.run {
        val stepSpec = HttpClientStepSpecificationImpl<Any, Any>()
        val requestBuilder = spyk<HttpRequestBuilder>()
        val ctx = StepTestHelper.createStepContext<String, String>(input = "input", minionId = "client-1")

        stepSpec.apply {
            request { _, _ ->
                form(HttpMethod.POST, "http://localhost:8000/form")
                    .addFormFields("tags", "alpha", "beta", "gamma")
            }
        }

        val request = stepSpec.requestFactory.invoke(requestBuilder, ctx, "") as FormHttpRequest
        request.toAsyncRequest(clientConfiguration)

        assertEquals(1, request.formFields().size)
        assertEquals(listOf("alpha", "beta", "gamma"), request.formFields()["tags"])
    }

}
