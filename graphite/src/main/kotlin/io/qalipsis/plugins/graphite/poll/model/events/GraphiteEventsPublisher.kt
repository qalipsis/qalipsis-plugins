/*
 * Copyright 2022 AERIS IT Solutions GmbH
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

package io.qalipsis.plugins.graphite.poll.model.events

import io.micronaut.context.annotation.Requires
import io.netty.channel.EventLoopGroup
import io.netty.channel.nio.NioEventLoopGroup
import io.qalipsis.api.Executors
import io.qalipsis.api.events.AbstractBufferedEventsPublisher
import io.qalipsis.api.events.Event
import io.qalipsis.api.pool.FixedPool
import jakarta.inject.Named
import jakarta.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking

/**
 * Implementation of [AbstractBufferedEventsPublisher] for [graphite][https://github.com/graphite-project].
 * Creates a list of [GraphiteEventsClient] based on configuration from [GraphiteEventsConfiguration].
 * Size may be configured by [configuration.publishers] field.
 *
 * @author rklymenko
 */
@Singleton
@Requires(beans = [GraphiteEventsConfiguration::class])
internal class GraphiteEventsPublisher(
    @Named(Executors.IO_EXECUTOR_NAME) private val coroutineScope: CoroutineScope,
    private val configuration: GraphiteEventsConfiguration
) : AbstractBufferedEventsPublisher(
    configuration.minLevel,
    configuration.lingerPeriod,
    configuration.batchSize,
    coroutineScope
) {

    private lateinit var workerGroup: EventLoopGroup

    private lateinit var clients: FixedPool<GraphiteEventsClient>

    /**
     * Builds [FixedPool]  of [GraphiteEventsClient] and starts [AbstractBufferedEventsPublisher].
     */
    override fun start() {
        workerGroup = NioEventLoopGroup()
        clients = FixedPool(configuration.publishers,
            checkOnAcquire = false,
            checkOnRelease = true,
            healthCheck = { it.isOpen }) {
            GraphiteEventsClient(
                configuration.protocol,
                configuration.host,
                configuration.port,
                workerGroup,
                coroutineScope
            ).open()
        }
        runBlocking(coroutineScope.coroutineContext) {
            clients.awaitReadiness()
        }
        super.start()
    }

    /**
     * Shutdowns [EventLoopGroup]
     * Closes [FixedPool] of [GraphiteEventsClient].
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
     * Publishes a list of [Event] using [FixedPool] of [GraphiteEventsClient]
     */
    override suspend fun publish(values: List<Event>) {
        clients.withPoolItem { client ->
            client.publish(values)
        }
    }

}
