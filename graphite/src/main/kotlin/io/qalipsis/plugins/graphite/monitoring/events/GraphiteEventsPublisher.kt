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

package io.qalipsis.plugins.graphite.monitoring.events

import io.micronaut.context.annotation.Requires
import io.netty.channel.EventLoopGroup
import io.netty.channel.nio.NioEventLoopGroup
import io.qalipsis.api.Executors
import io.qalipsis.api.events.AbstractBufferedEventsPublisher
import io.qalipsis.api.events.Event
import io.qalipsis.api.pool.FixedPool
import io.qalipsis.plugins.graphite.GraphiteProtocol
import io.qalipsis.plugins.graphite.client.GraphiteClient
import io.qalipsis.plugins.graphite.client.GraphiteTcpClient
import io.qalipsis.plugins.graphite.client.codecs.PickleEncoder
import io.qalipsis.plugins.graphite.client.codecs.PlaintextEncoder
import jakarta.inject.Named
import jakarta.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking

/**
 * Implementation of [AbstractBufferedEventsPublisher] for [graphite][https://github.com/graphite-project].
 *
 * @author rklymenko
 */
@Singleton
@Requires(beans = [GraphiteEventsConfiguration::class])
internal class GraphiteEventsPublisher(
    @Named(Executors.IO_EXECUTOR_NAME) private val coroutineScope: CoroutineScope,
    private val configuration: GraphiteEventsConfiguration,
) : AbstractBufferedEventsPublisher(
    configuration.minLevel,
    configuration.lingerPeriod,
    configuration.batchSize,
    coroutineScope
) {

    private lateinit var workerGroup: EventLoopGroup

    private lateinit var clients: FixedPool<GraphiteClient<Event>>

    /**
     * Builds [FixedPool] of [GraphiteClient]s and starts the publisher.
     */
    override fun start() {
        workerGroup = NioEventLoopGroup()
        val encoder = when (configuration.protocol) {
            GraphiteProtocol.PLAINTEXT -> PlaintextEncoder()
            GraphiteProtocol.PICKLE -> PickleEncoder()
        }
        clients = FixedPool(configuration.publishers,
            checkOnAcquire = false,
            checkOnRelease = true,
            healthCheck = { it.isOpen }) {
            GraphiteTcpClient<Event>(
                host = configuration.host,
                port = configuration.port,
                encoders = listOf(encoder, EventsEncoder(configuration.prefix, configuration.batchSize))
            ).open()
        }
        runBlocking(coroutineScope.coroutineContext) {
            clients.awaitReadiness()
        }
        super.start()
    }

    /**
     * Shutdowns [EventLoopGroup]
     * Closes [FixedPool] of [GraphiteClient].
     * Stops [AbstractBufferedEventsPublisher].
     */
    override fun stop() {
        workerGroup.shutdownGracefully()
        runBlocking(coroutineScope.coroutineContext) {
            clients.close()
        }
        super.stop()
    }

    /**
     * Publishes a list of [Event] using [FixedPool] of [GraphiteClient]
     */
    override suspend fun publish(values: List<Event>) {
        clients.withPoolItem { client ->
            client.send(values)
        }
    }

}
