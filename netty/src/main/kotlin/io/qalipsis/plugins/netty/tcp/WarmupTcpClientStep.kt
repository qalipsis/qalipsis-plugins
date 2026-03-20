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
