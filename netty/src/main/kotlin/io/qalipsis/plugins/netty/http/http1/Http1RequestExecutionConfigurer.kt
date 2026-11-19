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

package io.qalipsis.plugins.netty.http.http1

import io.netty.channel.ChannelPipeline
import io.netty.handler.codec.http.HttpResponse
import io.qalipsis.api.sync.ImmutableSlot
import io.qalipsis.plugins.netty.PipelineHandlerNames
import io.qalipsis.plugins.netty.http.HttpPipelineNames
import io.qalipsis.plugins.netty.http.client.HttpRequestExecutionConfigurer
import io.qalipsis.plugins.netty.http.client.monitoring.HttpChannelMonitoringHandler
import io.qalipsis.plugins.netty.http.client.monitoring.HttpStepContextBasedSocketMonitoringCollector
import io.qalipsis.plugins.netty.http.request.HttpRequest
import io.qalipsis.plugins.netty.http.request.InternalHttpRequest
import io.qalipsis.plugins.netty.http.spec.HttpClientConfiguration
import io.qalipsis.plugins.netty.monitoring.StepContextBasedSocketMonitoringCollector
import io.qalipsis.plugins.netty.socket.RequestWriter


/**
 * Implementation of [HttpRequestExecutionConfigurer] for HTTP/1.1.
 *
 * @author Eric Jessé
 */
internal class Http1RequestExecutionConfigurer(
    private val clientConfiguration: HttpClientConfiguration,
    private val pipeline: ChannelPipeline
) : HttpRequestExecutionConfigurer {

    private var channelMonitoringHandler: HttpChannelMonitoringHandler? = null

    private var responseHandler: Http1ResponseHandler? = null

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
            pipeline.addBefore(
                PipelineHandlerNames.CLIENT_CODEC,
                HttpPipelineNames.CHANNEL_MONITORING_HANDLER,
                cmh
            )
            channelMonitoringHandler = cmh

            val rh = Http1ResponseHandler(responseSlot, httpMonitoringCollector)
            pipeline.addLast(HttpPipelineNames.INBOUND_HANDLER, rh)
            responseHandler = rh
        } else {
            // Subsequent requests: reset handler state.
            existingCmh.prepare(monitoringCollector)
            responseHandler!!.prepare(responseSlot, httpMonitoringCollector)
        }

        return Http1RequestWriter(
            (request as InternalHttpRequest<*, *>).toNettyRequest(clientConfiguration),
            responseSlot,
            monitoringCollector
        )
    }

    override fun completeMonitoring() {
        channelMonitoringHandler?.complete()
    }
}
