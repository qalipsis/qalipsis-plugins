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

package io.qalipsis.plugins.graphite.monitoring.meters

import io.micronaut.context.annotation.Requires
import io.netty.channel.EventLoopGroup
import io.netty.channel.nio.NioEventLoopGroup
import io.qalipsis.api.meters.MeasurementPublisher
import io.qalipsis.api.meters.MeterSnapshot
import io.qalipsis.api.pool.FixedPool
import io.qalipsis.plugins.graphite.GraphiteProtocol
import io.qalipsis.plugins.graphite.client.GraphiteClient
import io.qalipsis.plugins.graphite.client.GraphiteTcpClient
import io.qalipsis.plugins.graphite.client.codecs.PickleEncoder
import io.qalipsis.plugins.graphite.client.codecs.PlaintextEncoder

/**
 * Exports a given collection of meter snapshots too graphite based on the configurations specified in the [GraphiteMeasurementConfiguration].
 * Size may be configured by [configuration.publishers] field.
 *
 * @author Francisca Eze
 */
@Requires(beans = [GraphiteMeasurementConfiguration::class])
internal class GraphiteMeasurementPublisher(
    private val configuration: GraphiteMeasurementConfiguration,
) : MeasurementPublisher {

    private lateinit var workerGroup: EventLoopGroup

    private lateinit var clients: FixedPool<GraphiteClient<MeterSnapshot>>

    /**
     * Builds [FixedPool] of [GraphiteClient].
     */
    override suspend fun init() {
        workerGroup = NioEventLoopGroup()
        val encoder = when (configuration.protocol) {
            GraphiteProtocol.PLAINTEXT -> PlaintextEncoder()
            GraphiteProtocol.PICKLE -> PickleEncoder()
        }
        clients = FixedPool(configuration.publishers,
            checkOnAcquire = false,
            checkOnRelease = true,
            healthCheck = { it.isOpen }) {
            GraphiteTcpClient<MeterSnapshot>(
                host = configuration.host,
                port = configuration.port,
                encoders = listOf(encoder, MeterSnapshotsEncoder(configuration.prefix, configuration.batchSize))
            ).open()
        }
        clients.awaitReadiness()
    }

    /**
     * Shutdowns [EventLoopGroup]
     * Closes [FixedPool] of [GraphiteClient].
     */
    override suspend fun stop() {
        clients.close()
        workerGroup.shutdownGracefully()
    }

    /**
     * Publishes a collection of [MeterSnapshot] using [FixedPool] of [GraphiteClient]
     */
    override suspend fun publish(meters: Collection<MeterSnapshot>) {
        clients.withPoolItem { client ->
            client.send(meters)
        }
    }

}
