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

package io.qalipsis.plugins.http.monitoring

import assertk.all
import assertk.assertThat
import assertk.assertions.isGreaterThan
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.justRun
import io.mockk.mockk
import io.qalipsis.plugins.http.client.MonitoringResponseConsumer
import io.qalipsis.test.mockk.WithMockk
import org.apache.hc.client5.http.async.methods.SimpleHttpResponse
import org.apache.hc.client5.http.protocol.HttpClientContext
import org.apache.hc.core5.concurrent.FutureCallback
import org.apache.hc.core5.http.EntityDetails
import org.apache.hc.core5.http.HttpResponse
import org.apache.hc.core5.http.message.BasicHttpResponse
import org.apache.hc.core5.http.protocol.HttpContext
import org.junit.jupiter.api.Test

@WithMockk
internal class MonitoringResponseConsumerTest {

    @MockK
    private lateinit var resultCallback: FutureCallback<SimpleHttpResponse>

    @Test
    fun `should set first-byte and last-byte attributes when response has no entity`() {
        justRun { resultCallback.completed(any()) }
        val consumer = MonitoringResponseConsumer.create()
        val context: HttpContext = HttpClientContext.create()
        val response: HttpResponse = BasicHttpResponse(200)

        consumer.consumeResponse(response, null, context, resultCallback)

        assertThat(context.getAttribute(MonitoringResponseConsumer.FIRST_BYTE_ATTR)).isNotNull().all {
            isInstanceOf(Long::class).isGreaterThan(0L)
        }
        assertThat(context.getAttribute(MonitoringResponseConsumer.LAST_BYTE_ATTR)).isNotNull().all {
            isInstanceOf(Long::class).isGreaterThan(0L)
        }
    }

    @Test
    fun `should set first-byte but not last-byte when response has entity`() {
        val consumer = MonitoringResponseConsumer.create()
        val context: HttpContext = HttpClientContext.create()
        val response: HttpResponse = BasicHttpResponse(200)
        val entityDetails = mockk<EntityDetails> {
            every { contentLength } returns 128L
            every { contentType } returns "text/plain"
            every { contentEncoding } returns null
            every { isChunked } returns false
            every { trailerNames } returns emptySet()
        }

        consumer.consumeResponse(response, entityDetails, context, resultCallback)

        assertThat(context.getAttribute(MonitoringResponseConsumer.FIRST_BYTE_ATTR)).isNotNull().all {
            isInstanceOf(Long::class).isGreaterThan(0L)
        }
        // last-byte should not be set yet — it's set in streamEnd().
        assertThat(context.getAttribute(MonitoringResponseConsumer.LAST_BYTE_ATTR)).isNull()
    }

    @Test
    fun `should set last-byte on streamEnd`() {
        justRun { resultCallback.completed(any()) }
        val consumer = MonitoringResponseConsumer.create()
        val context: HttpContext = HttpClientContext.create()
        val response: HttpResponse = BasicHttpResponse(200)
        val entityDetails = mockk<EntityDetails> {
            every { contentLength } returns 128L
            every { contentType } returns "text/plain"
            every { contentEncoding } returns null
            every { isChunked } returns false
            every { trailerNames } returns emptySet()
        }

        consumer.consumeResponse(response, entityDetails, context, resultCallback)
        consumer.streamEnd(mutableListOf())

        assertThat(context.getAttribute(MonitoringResponseConsumer.LAST_BYTE_ATTR)).isNotNull().all {
            isInstanceOf(Long::class).isGreaterThan(0L)
        }
    }
}
