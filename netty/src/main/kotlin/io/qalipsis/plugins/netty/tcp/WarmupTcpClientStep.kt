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

package io.qalipsis.plugins.netty.tcp

import io.netty.channel.EventLoopGroup
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.context.StepName
import io.qalipsis.api.events.EventsLogger
import io.qalipsis.api.meters.CampaignMeterRegistry
import io.qalipsis.api.retry.RetryPolicy
import io.qalipsis.plugins.netty.ByteArrayRequestBuilder
import io.qalipsis.plugins.netty.ByteArrayRequestBuilderImpl
import io.qalipsis.plugins.netty.EventLoopGroupSupplier
import io.qalipsis.plugins.netty.monitoring.StepBasedTcpMonitoringCollector
import io.qalipsis.plugins.netty.socket.WarmupSocketClientStep
import io.qalipsis.plugins.netty.tcp.client.TcpClient
import io.qalipsis.plugins.netty.tcp.spec.TcpClientConfiguration

/**
 * Step to send and receive data using TCP, using a warmup connection strategy where
 * all connections are pre-created at start and assigned to minions on demand.
 *
 * @author Eric Jesse
 */
internal class WarmupTcpClientStep<I>(
    id: StepName,
    retryPolicy: RetryPolicy?,
    requestFactory: suspend ByteArrayRequestBuilder.(StepContext<*, *>, I) -> ByteArray,
    private val clientConfiguration: TcpClientConfiguration,
    shared: Boolean,
    eventLoopGroupSupplier: EventLoopGroupSupplier,
    eventsLogger: EventsLogger?,
    meterRegistry: CampaignMeterRegistry?,
) : WarmupSocketClientStep<I, ByteArray, TcpClientConfiguration, ByteArray, ByteArray, ByteArrayRequestBuilder, TcpClient>(
    id,
    retryPolicy,
    ByteArrayRequestBuilderImpl,
    requestFactory,
    clientConfiguration,
    shared,
    "tcp",
    eventLoopGroupSupplier,
    eventsLogger,
    meterRegistry
), TcpClientStep<I, ConnectionAndRequestResult<I, ByteArray>> {

    override suspend fun createClient(
        workerGroup: EventLoopGroup,
        monitoringCollector: StepBasedTcpMonitoringCollector,
    ): TcpClient {
        val cli = TcpClient(Long.MAX_VALUE)
        cli.open(clientConfiguration, workerGroup, monitoringCollector)
        return cli
    }
}
