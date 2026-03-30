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
