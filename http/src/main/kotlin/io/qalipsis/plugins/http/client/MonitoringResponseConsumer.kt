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

package io.qalipsis.plugins.http.client

import java.nio.ByteBuffer
import org.apache.hc.client5.http.async.methods.SimpleHttpResponse
import org.apache.hc.client5.http.async.methods.SimpleResponseConsumer
import org.apache.hc.core5.concurrent.FutureCallback
import org.apache.hc.core5.http.EntityDetails
import org.apache.hc.core5.http.Header
import org.apache.hc.core5.http.HttpResponse
import org.apache.hc.core5.http.nio.AsyncResponseConsumer
import org.apache.hc.core5.http.nio.CapacityChannel
import org.apache.hc.core5.http.protocol.HttpContext

/**
 * Wrapper around [SimpleResponseConsumer] that records monitoring timestamps
 * in the [HttpContext] for time-to-first-byte and time-to-last-byte tracking.
 *
 * @author Eric Jessé
 */
internal class MonitoringResponseConsumer private constructor(
    private val delegate: AsyncResponseConsumer<SimpleHttpResponse> = SimpleResponseConsumer.create(),
) : AsyncResponseConsumer<SimpleHttpResponse> {

    private var httpContext: HttpContext? = null

    override fun consumeResponse(
        response: HttpResponse,
        entityDetails: EntityDetails?,
        context: HttpContext,
        resultCallback: FutureCallback<SimpleHttpResponse>,
    ) {
        httpContext = context
        val now = System.nanoTime()
        context.setAttribute(FIRST_BYTE_ATTR, now)
        if (entityDetails == null) {
            // No body — first byte and last byte are the same (e.g., HEAD, 204).
            context.setAttribute(LAST_BYTE_ATTR, now)
        }
        delegate.consumeResponse(response, entityDetails, context, resultCallback)
    }

    override fun informationResponse(response: HttpResponse, context: HttpContext) {
        delegate.informationResponse(response, context)
    }

    override fun failed(cause: Exception) {
        delegate.failed(cause)
    }

    override fun updateCapacity(capacityChannel: CapacityChannel) {
        delegate.updateCapacity(capacityChannel)
    }

    override fun consume(src: ByteBuffer) {
        delegate.consume(src)
    }

    override fun streamEnd(trailers: MutableList<out Header>?) {
        httpContext?.setAttribute(LAST_BYTE_ATTR, System.nanoTime())
        delegate.streamEnd(trailers)
    }

    override fun releaseResources() {
        delegate.releaseResources()
    }

    companion object {

        const val FIRST_BYTE_ATTR = "monitoring.first-byte"

        const val LAST_BYTE_ATTR = "monitoring.last-byte"

        fun create(): MonitoringResponseConsumer = MonitoringResponseConsumer()
    }
}
