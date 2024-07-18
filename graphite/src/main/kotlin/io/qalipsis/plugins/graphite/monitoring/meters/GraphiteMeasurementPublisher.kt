/*
 * Copyright 2024 AERIS IT Solutions GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing
 * permissions and limitations under the License.
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
