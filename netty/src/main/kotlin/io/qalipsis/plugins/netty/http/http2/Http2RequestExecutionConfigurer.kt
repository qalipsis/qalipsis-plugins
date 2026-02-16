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

package io.qalipsis.plugins.netty.http.http2

import io.netty.channel.ChannelPipeline
import io.netty.handler.codec.http.HttpResponse
import io.qalipsis.api.sync.ImmutableSlot
import io.qalipsis.plugins.netty.http.HttpPipelineNames.CHANNEL_MONITORING_HANDLER
import io.qalipsis.plugins.netty.http.HttpPipelineNames.INBOUND_HANDLER
import io.qalipsis.plugins.netty.http.client.HttpRequestExecutionConfigurer
import io.qalipsis.plugins.netty.http.client.monitoring.HttpChannelMonitoringHandler
import io.qalipsis.plugins.netty.http.client.monitoring.HttpStepContextBasedSocketMonitoringCollector
import io.qalipsis.plugins.netty.http.request.HttpRequest
import io.qalipsis.plugins.netty.http.request.InternalHttpRequest
import io.qalipsis.plugins.netty.http.spec.HttpClientConfiguration
import io.qalipsis.plugins.netty.monitoring.StepContextBasedSocketMonitoringCollector
import io.qalipsis.plugins.netty.socket.RequestWriter

/**
 * Implementation of [HttpRequestExecutionConfigurer] for HTTP/2.
 *
 * @author Eric Jessé
 */
internal class Http2RequestExecutionConfigurer(
    private val clientConfiguration: HttpClientConfiguration,
    private val pipeline: ChannelPipeline
) : HttpRequestExecutionConfigurer {

    private var streamIdGenerator = Http2ClientStreamIdGeneratorImpl()

    private var channelMonitoringHandler: HttpChannelMonitoringHandler? = null

    private var responseHandler: Http2ResponseHandler? = null

    override fun configure(
        request: HttpRequest<*>,
        monitoringCollector: StepContextBasedSocketMonitoringCollector,
        responseSlot: ImmutableSlot<Result<HttpResponse>>
    ): RequestWriter {
        val httpMonitoringCollector = monitoringCollector as HttpStepContextBasedSocketMonitoringCollector

        val existingCmh = channelMonitoringHandler
        if (existingCmh == null) {
            // First request: install handlers into the pipeline.
            val cmh = HttpChannelMonitoringHandler(monitoringCollector)
            pipeline.addFirst(CHANNEL_MONITORING_HANDLER, cmh)
            channelMonitoringHandler = cmh

            val rh = Http2ResponseHandler(responseSlot, httpMonitoringCollector)
            pipeline.addLast(INBOUND_HANDLER, rh)
            responseHandler = rh
        } else {
            // Subsequent requests: reset handler state.
            existingCmh.prepare(monitoringCollector)
            responseHandler!!.prepare(responseSlot, httpMonitoringCollector)
        }

        return Http2RequestWriter(
            (request as InternalHttpRequest<*, *>).toNettyRequest(clientConfiguration),
            responseSlot,
            monitoringCollector,
            if (clientConfiguration.isSecure) "https" else "http",
            streamIdGenerator
        )
    }

    override fun completeMonitoring() {
        channelMonitoringHandler?.complete()
    }
}
