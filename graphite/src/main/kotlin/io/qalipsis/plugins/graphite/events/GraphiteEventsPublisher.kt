package io.qalipsis.plugins.graphite.events

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
 * Creates a list of [GraphiteClient] based on configuration from [GraphiteEventsConfiguration].
 * Size may be configured by [configuration.publishers] field.
 *
 * @author rklymenko
 */
@Singleton
@Requires(beans = [GraphiteEventsConfiguration::class])
internal class GraphiteEventsPublisher(
    @Named(Executors.BACKGROUND_EXECUTOR_NAME) private val coroutineScope: CoroutineScope,
    private val configuration: GraphiteEventsConfiguration
) : AbstractBufferedEventsPublisher(
    configuration.minLevel,
    configuration.lingerPeriod,
    configuration.batchSize,
    coroutineScope
) {

    private lateinit var workerGroup: EventLoopGroup

    private lateinit var clients: FixedPool<GraphiteClient>

    /**
     * Builds [FixedPool]  of [GraphiteClient] and starts [AbstractBufferedEventsPublisher].
     */
    override fun start() {
        workerGroup = NioEventLoopGroup()
        clients = FixedPool(configuration.publishers,
            checkOnAcquire = true,
            checkOnRelease = true,
            healthCheck = { it.isOpen }) {
            GraphiteClient(
                configuration.protocol,
                configuration.host,
                configuration.port,
                workerGroup
            ).start()
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
            client.publish(values)
        }
    }

}
