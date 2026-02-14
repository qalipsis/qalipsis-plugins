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
