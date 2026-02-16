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

package io.qalipsis.plugins.netty.http.http1

import io.netty.channel.ChannelHandlerContext
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.handler.codec.http.FullHttpResponse
import io.netty.handler.codec.http.HttpResponse
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.sync.ImmutableSlot
import io.qalipsis.plugins.netty.http.client.monitoring.HttpStepContextBasedSocketMonitoringCollector

/**
 * Handler for responses for HTTP 1.1.
 *
 * @author Eric Jessé
 */
internal class Http1ResponseHandler(
    responseSlot: ImmutableSlot<Result<HttpResponse>>,
    monitoringCollector: HttpStepContextBasedSocketMonitoringCollector,
) : SimpleChannelInboundHandler<FullHttpResponse>() {

    private var responseSlot: ImmutableSlot<Result<HttpResponse>> = responseSlot

    private var monitoringCollector: HttpStepContextBasedSocketMonitoringCollector = monitoringCollector

    fun prepare(
        responseSlot: ImmutableSlot<Result<HttpResponse>>,
        monitoringCollector: HttpStepContextBasedSocketMonitoringCollector
    ) {
        this.responseSlot = responseSlot
        this.monitoringCollector = monitoringCollector
    }

    override fun channelRead0(ctx: ChannelHandlerContext, msg: FullHttpResponse) {
        monitoringCollector.recordReceptionComplete()
        monitoringCollector.recordHttpStatus(msg.status())
        msg.touch()
        msg.retain()
        if (responseSlot.isEmpty()) {
            responseSlot.offer(Result.success(msg))
        } else {
            msg.release()
        }
    }

    override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
        log.trace(cause) { "An exception occurred while processing the HTTP 1.1 response: ${cause.message}" }
        if (responseSlot.isEmpty()) {
            responseSlot.offer(Result.failure(cause))
        }
    }

    companion object {

        @JvmStatic
        private val log = logger()

    }
}
