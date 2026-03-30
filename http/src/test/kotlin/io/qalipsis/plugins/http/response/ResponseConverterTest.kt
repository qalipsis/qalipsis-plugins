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

import assertk.all
import assertk.assertThat
import assertk.assertions.hasSameSizeAs
import assertk.assertions.hasSize
import assertk.assertions.index
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import assertk.assertions.isSameInstanceAs
import assertk.assertions.key
import assertk.assertions.prop
import io.aerisconsulting.catadioptre.invokeInvisible
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import io.mockk.slot
import io.mockk.spyk
import io.mockk.verify
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.verifyNever
import org.apache.hc.client5.http.async.methods.SimpleHttpResponse
import org.apache.hc.client5.http.cookie.Cookie
import org.apache.hc.client5.http.protocol.HttpClientContext
import org.apache.hc.core5.http.ContentType
import org.junit.jupiter.api.Test

@WithMockk
internal class ResponseConverterTest {

    @MockK
    private lateinit var deserializer1: HttpBodyDeserializer

    @MockK
    private lateinit var deserializer2: HttpBodyDeserializer

    @Test
    internal fun `should convert the response with the deserialized body`() {
        // given
        val bytes = "This is the content".toByteArray()
        val httpResponse = spyk(
            SimpleHttpResponse.create(201, bytes).apply {
                addHeader("Content-Type", MediaType.TEXT_PLAIN + ";charset=UTF-8")
                addHeader("Content-Encoding", "gzip")
                addHeader("Content-Length", "19")
            }
        )
        val clientContext = mockk<HttpClientContext> {
            every { cookieStore } returns mockk {
                every { cookies } returns listOf(
                    mockk {
                        every { name } returns "yummy_cookie"
                        every { value } returns "choco"
                    },
                    mockk {
                        every { name } returns "tasty_cookie"
                        every { value } returns "strawberry"
                    }
                )
            }
        }

        val deserializedBody = Entity("value")
        val responseConverter = spyk(ResponseConverter<Entity>(Entity::class, emptyList()), recordPrivateCalls = true)
        val capturedBytes = slot<ByteArray>()
        every {
            responseConverter["convertBody"](
                refEq(httpResponse),
                capture(capturedBytes),
                any<MediaType>()
            )
        } returns deserializedBody

        // when
        val response = responseConverter.convert(httpResponse, clientContext)

        // then
        assertThat(capturedBytes.captured).all {
            hasSameSizeAs(bytes)
            repeat(bytes.size) { index(it).isEqualTo(bytes[it]) }
        }
        assertThat(response).all {
            prop(HttpResponse<Entity>::reason).isEqualTo("Created")
            prop(HttpResponse<Entity>::code).isEqualTo(201)
            prop(HttpResponse<Entity>::body).isSameInstanceAs(deserializedBody)
            prop(HttpResponse<Entity>::bodyBytes).isSameInstanceAs(capturedBytes.captured)
            prop(HttpResponse<Entity>::contentType).isEqualTo(ContentType.TEXT_PLAIN)
            prop(HttpResponse<Entity>::cookies).all {
                hasSize(2)
                key("yummy_cookie").prop(Cookie::getValue).isEqualTo("choco")
                key("tasty_cookie").prop(Cookie::getValue).isEqualTo("strawberry")
            }
            prop(HttpResponse<Entity>::headers).all {
                hasSize(3)
                key("Content-Length").isEqualTo("19")
                key("Content-Encoding").isEqualTo("gzip")
                key("Content-Type").isEqualTo(MediaType.TEXT_PLAIN + ";charset=UTF-8")
            }
        }
        verify {
            responseConverter["convertBody"](
                refEq(httpResponse),
                refEq(capturedBytes.captured),
                eq(MediaType(MediaType.TEXT_PLAIN + ";charset=UTF-8"))
            )
        }
    }

    @Test
    internal fun `should convert the response with the deserialized body and the default content type`() {
        // given
        val bytes = "This is the content".toByteArray()
        val httpResponse = spyk(
            SimpleHttpResponse.create(202, bytes).apply {
                addHeader("Content-Encoding", "gzip")
                addHeader("Content-Length", "19")
            }
        )
        val clientContext = mockk<HttpClientContext> {
            every { cookieStore } returns mockk {
                every { cookies } returns listOf(
                    mockk {
                        every { name } returns "yummy_cookie"
                        every { value } returns "choco"
                    },
                    mockk {
                        every { name } returns "tasty_cookie"
                        every { value } returns "strawberry"
                    }
                )
            }
        }
        val deserializedBody = Entity("value")
        val responseConverter = spyk(ResponseConverter<Entity>(Entity::class, emptyList()), recordPrivateCalls = true)
        val capturedBytes = slot<ByteArray>()
        every {
            responseConverter["convertBody"](
                refEq(httpResponse),
                capture(capturedBytes),
                any<MediaType>()
            )
        } returns deserializedBody

        // when
        val response = responseConverter.convert(httpResponse, clientContext)

        // then
        assertThat(capturedBytes.captured).all {
            hasSameSizeAs(bytes)
            repeat(bytes.size) { index(it).isEqualTo(bytes[it]) }
        }
        assertThat(response).all {
            prop(HttpResponse<Entity>::reason).isEqualTo("Accepted")
            prop(HttpResponse<Entity>::code).isEqualTo(202)
            prop(HttpResponse<Entity>::body).isSameInstanceAs(deserializedBody)
            prop(HttpResponse<Entity>::bodyBytes).isSameInstanceAs(capturedBytes.captured)
            prop(HttpResponse<Entity>::contentType).isEqualTo(ContentType.TEXT_PLAIN)
            prop(HttpResponse<Entity>::cookies).all {
                hasSize(2)
                key("yummy_cookie").prop(Cookie::getValue).isEqualTo("choco")
                key("tasty_cookie").prop(Cookie::getValue).isEqualTo("strawberry")
            }
            prop(HttpResponse<Entity>::headers).all {
                hasSize(2)
                key("Content-Length").isEqualTo("19")
                key("Content-Encoding").isEqualTo("gzip")
            }
        }
        verify {
            responseConverter["convertBody"](
                refEq(httpResponse),
                refEq(capturedBytes.captured),
                eq(MediaType.TEXT_PLAIN_TYPE)
            )
        }
    }

    @Test
    internal fun `should convert a non full http response`() {
        // given
        val httpResponse = spyk(
            SimpleHttpResponse.create(202).apply {
                // Mix cases in the headers names.
                addHeader("Set-CoOkie", "yummy_cookie=choco")
                addHeader("Set-CoOkie", "tasty_cookie=strawberry")
                addHeader("content-length", "19")
                addHeader("Content-Encoding", "gzip")
            }
        )
        val clientContext = mockk<HttpClientContext> {
            every { cookieStore } returns mockk { every { cookies } returns emptyList() }
        }
        val responseConverter = spyk(ResponseConverter<Entity>(Entity::class, emptyList()), recordPrivateCalls = true)

        // when
        val response = responseConverter.convert(httpResponse, clientContext)

        // then
        verifyNever {
            responseConverter["convertBody"](
                any<SimpleHttpResponse>(),
                any<ByteArray>(),
                any<MediaType>()
            )
        }
        assertThat(response).all {
            prop(HttpResponse<Entity>::reason).isSameInstanceAs("Accepted")
            prop(HttpResponse<Entity>::code).isEqualTo(202)
            prop(HttpResponse<Entity>::body).isNull()
            prop(HttpResponse<Entity>::bodyBytes).isNull()
            prop(HttpResponse<Entity>::contentType).isNull()
            prop(HttpResponse<Entity>::cookies).isEmpty()
            prop(HttpResponse<Entity>::headers).all {
                hasSize(3)
                key("Set-CoOkie").isEqualTo("tasty_cookie=strawberry")
                key("content-length").isEqualTo("19")
                key("Content-Encoding").isEqualTo("gzip")
            }
        }
    }

    @Test
    internal fun `should convert the body to Unit`() {
        // given
        val bytes = "This is the content ÉÖß".toByteArray()
        val mediaType = MediaType.APPLICATION_JSON_TYPE
        val httpResponse = spyk(SimpleHttpResponse.create(202, bytes)) {
            every { code } returns 202
        }
        val responseConverter = ResponseConverter<Unit>(Unit::class, listOf(deserializer1, deserializer2))

        // when
        val body: Unit = responseConverter.invokeInvisible("convertBody", httpResponse, bytes, mediaType)

        // then
        assertThat(body).isEqualTo(Unit)
        confirmVerified(deserializer1, deserializer2)
    }

    @Test
    internal fun `should convert the body to a string with the provided encoding`() {
        // given
        val bytes = "This is the content ÉÖß".toByteArray(Charsets.UTF_32BE)
        val mediaType = MediaType("any/any; charset=${Charsets.UTF_32BE}")
        val httpResponse = spyk(SimpleHttpResponse.create(202, bytes)) {
            every { code } returns 202
        }
        val responseConverter = ResponseConverter<String>(String::class, listOf(deserializer1, deserializer2))

        // when
        val body: String = responseConverter.invokeInvisible("convertBody", httpResponse, bytes, mediaType)

        // then
        assertThat(body).isEqualTo("This is the content ÉÖß")
        confirmVerified(deserializer1, deserializer2)
    }

    @Test
    internal fun `should return the byte array as body`() {
        // given
        val bytes = "This is the content ÉÖß".toByteArray(Charsets.UTF_32BE)
        val mediaType = MediaType("any/any; charset=${Charsets.UTF_32BE}")
        val httpResponse = spyk(SimpleHttpResponse.create(202, bytes)) {
            every { code } returns 202
        }
        val responseConverter = ResponseConverter<ByteArray>(ByteArray::class, listOf(deserializer1, deserializer2))

        // when
        val body: ByteArray = responseConverter.invokeInvisible("convertBody", httpResponse, bytes, mediaType)

        // then
        assertThat(body).isSameInstanceAs(bytes)
        confirmVerified(deserializer1, deserializer2)
    }

    @Test
    internal fun `should deserialize as a null body when request is not a success`() {
        // given
        val bytes = "This is the content ÉÖß".toByteArray(Charsets.UTF_32BE)
        val mediaType = MediaType("any/any; charset=${Charsets.UTF_32BE}")
        val httpResponse = spyk(SimpleHttpResponse.create(500, bytes)) {
            every { code } returns 500
        }
        val responseConverter = ResponseConverter<ByteArray>(ByteArray::class, listOf(deserializer1, deserializer2))

        // when
        val body: ByteArray? = responseConverter.invokeInvisible("convertBody", httpResponse, bytes, mediaType)

        // then
        assertThat(body).isNull()
        confirmVerified(deserializer1, deserializer2)
    }

    @Test
    internal fun `should deserialize only with the matching deserializer`() {
        // given
        val bytes = "This is the content ÉÖß".toByteArray(Charsets.UTF_32BE)
        val mediaType = MediaType.APPLICATION_JSON_TYPE
        val httpResponse = spyk(SimpleHttpResponse.create(202, bytes)) {
            every { code } returns 202
        }
        val result = Entity("value")
        every { deserializer1.accept(refEq(mediaType)) } returns false
        every { deserializer2.accept(refEq(mediaType)) } returns true
        every { deserializer2.convert(refEq(bytes), refEq(mediaType), refEq(Entity::class)) } returns result

        val responseConverter = ResponseConverter<Entity>(Entity::class, listOf(deserializer1, deserializer2))

        // when
        val body: Entity? = responseConverter.invokeInvisible("convertBody", httpResponse, bytes, mediaType)

        // then
        assertThat(body).isSameInstanceAs(result)
        verify {
            deserializer1.accept(refEq(mediaType))
            deserializer2.accept(refEq(mediaType))
            deserializer2.convert(refEq(bytes), refEq(mediaType), refEq(Entity::class))
        }
        confirmVerified(deserializer1, deserializer2)

    }

    @Test
    internal fun `should deserialize with the first working matching deserializer`() {
        // given
        val bytes = "This is the content ÉÖß".toByteArray(Charsets.UTF_32BE)
        val mediaType = MediaType.APPLICATION_JSON_TYPE
        val httpResponse = spyk(SimpleHttpResponse.create(202, bytes)) {
            every { code } returns 202
        }
        val result = Entity("value")
        every { deserializer1.accept(refEq(mediaType)) } returns true
        every { deserializer1.convert(refEq(bytes), refEq(mediaType), refEq(Entity::class)) } throws RuntimeException()
        every { deserializer2.accept(refEq(mediaType)) } returns true
        every { deserializer2.convert(refEq(bytes), refEq(mediaType), refEq(Entity::class)) } returns result

        val responseConverter = ResponseConverter<Entity>(Entity::class, listOf(deserializer1, deserializer2))

        // when
        val body: Entity? = responseConverter.invokeInvisible("convertBody", httpResponse, bytes, mediaType)

        // then
        assertThat(body).isSameInstanceAs(result)
        verify {
            deserializer1.accept(refEq(mediaType))
            deserializer1.convert(refEq(bytes), refEq(mediaType), refEq(Entity::class))
            deserializer2.accept(refEq(mediaType))
            deserializer2.convert(refEq(bytes), refEq(mediaType), refEq(Entity::class))
        }
        confirmVerified(deserializer1, deserializer2)
    }

    @Test
    internal fun `should deserialize as a null body when no serializer succeeds`() {
        // given
        val bytes = "This is the content ÉÖß".toByteArray(Charsets.UTF_32BE)
        val mediaType = MediaType.APPLICATION_JSON_TYPE
        val httpResponse = spyk(SimpleHttpResponse.create(202, bytes)) {
            every { code } returns 202
        }
        every { deserializer1.accept(refEq(mediaType)) } returns true
        every { deserializer1.convert(refEq(bytes), refEq(mediaType), refEq(Entity::class)) } throws RuntimeException()
        every { deserializer2.accept(refEq(mediaType)) } returns true
        every { deserializer2.convert(refEq(bytes), refEq(mediaType), refEq(Entity::class)) } throws RuntimeException()

        val responseConverter = ResponseConverter<Entity>(Entity::class, listOf(deserializer1, deserializer2))

        // when
        val body: Entity? = responseConverter.invokeInvisible("convertBody", httpResponse, bytes, mediaType)

        // then
        assertThat(body).isNull()
        verify {
            deserializer1.accept(refEq(mediaType))
            deserializer1.convert(refEq(bytes), refEq(mediaType), refEq(Entity::class))
            deserializer2.accept(refEq(mediaType))
            deserializer2.convert(refEq(bytes), refEq(mediaType), refEq(Entity::class))
        }
        confirmVerified(deserializer1, deserializer2)
    }

    private data class Entity(val field: String)
}
