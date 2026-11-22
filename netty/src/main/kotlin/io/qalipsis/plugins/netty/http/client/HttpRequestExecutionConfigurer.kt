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

package io.qalipsis.plugins.netty.http.client

import io.netty.channel.ChannelPipeline
import io.netty.handler.codec.http.HttpResponse
import io.qalipsis.api.sync.ImmutableSlot
import io.qalipsis.plugins.netty.monitoring.StepContextBasedSocketMonitoringCollector
import io.qalipsis.plugins.netty.socket.RequestWriter

/**
 * Service in charge of configuring the different components (Netty and QALIPSIS) when a HTTP request is being executed.
 *
 * @author Eric Jessé
 */
internal interface HttpRequestExecutionConfigurer {

    fun configure(
        request: io.qalipsis.plugins.netty.http.request.HttpRequest<*>,
        monitoringCollector: StepContextBasedSocketMonitoringCollector,
        responseSlot: ImmutableSlot<Result<HttpResponse>>
    ): RequestWriter

    /**
     * Signals that the current request cycle is complete. Prevents the monitoring handler
     * from recording protocol-level writes (e.g. HTTP/2 GOAWAY, TLS close_notify) that
     * occur after the request/response exchange.
     */
    fun completeMonitoring() {}

}

/**
 * Executes [action] on the event loop of the channel: directly when already there, otherwise after the tasks and the
 * I/O events already queued, so that the state of the handlers is only changed between two exchanges.
 */
internal fun ChannelPipeline.runOnEventLoop(action: () -> Unit) {
    val eventLoop = channel().eventLoop()
    if (eventLoop.inEventLoop()) action() else eventLoop.execute(action)
}
