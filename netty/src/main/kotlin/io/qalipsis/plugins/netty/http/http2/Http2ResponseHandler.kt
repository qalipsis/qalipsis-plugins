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

package io.qalipsis.plugins.netty.http.http2

import io.netty.channel.ChannelHandlerContext
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.handler.codec.http.HttpResponse
import io.netty.util.ReferenceCountUtil
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.sync.ImmutableSlot
import io.qalipsis.plugins.netty.http.client.monitoring.HttpStepContextBasedSocketMonitoringCollector

/**
 * Handler for responses for HTTP 2.0.
 *
 * @author Eric Jessé
 */
internal class Http2ResponseHandler(
    responseSlot: ImmutableSlot<Result<HttpResponse>>,
    monitoringCollector: HttpStepContextBasedSocketMonitoringCollector
) : SimpleChannelInboundHandler<HttpResponse>() {

    private var responseSlot: ImmutableSlot<Result<HttpResponse>> = responseSlot

    private var monitoringCollector: HttpStepContextBasedSocketMonitoringCollector = monitoringCollector

    fun prepare(
        responseSlot: ImmutableSlot<Result<HttpResponse>>,
        monitoringCollector: HttpStepContextBasedSocketMonitoringCollector
    ) {
        this.responseSlot = responseSlot
        this.monitoringCollector = monitoringCollector
    }

    override fun channelRead0(ctx: ChannelHandlerContext, msg: HttpResponse) {
        log.trace { "Received a HTTP/2 response: $msg" }
        ReferenceCountUtil.touch(msg)
        ReferenceCountUtil.retain(msg)

        monitoringCollector.recordReceptionComplete()
        monitoringCollector.recordHttpStatus(msg.status())
        if (responseSlot.isEmpty()) {
            responseSlot.offer(Result.success(msg))
        } else {
            ReferenceCountUtil.release(msg)
        }
    }

    override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
        log.trace(cause) { "An exception occurred while processing the HTTP 2.0 response: ${cause.message}" }
        if (responseSlot.isEmpty()) {
            responseSlot.offer(Result.failure(cause))
        }
    }

    companion object {

        @JvmStatic
        private val log = logger()

    }
}
