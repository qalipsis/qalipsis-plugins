package io.qalipsis.plugins.graphite.save

import io.netty.bootstrap.Bootstrap
import io.netty.channel.Channel
import io.netty.channel.ChannelFuture
import io.netty.channel.ChannelInitializer
import io.netty.channel.ChannelOption
import io.netty.channel.EventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioSocketChannel
import io.qalipsis.api.io.Closeable
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.sync.ImmutableSlot
import io.qalipsis.plugins.graphite.save.codecs.GraphitePlaintextStringEncoder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * Abstraction over [Channel] which uses a common [EventLoopGroup]
 *
 * @author rklymenko
 */
internal class GraphiteSaveClient(
    private val host: String,
    private val port: Int,
    private val workerGroup: EventLoopGroup,
    private val coroutineScope: CoroutineScope
) : Closeable {

    private var started = false

    private lateinit var channelFuture: ChannelFuture

    protected val channel: Channel
        get() = channelFuture.channel()

    val isOpen: Boolean
        get() = started && channel.isOpen

    suspend fun start(): GraphiteSaveClient {
        started = false
        val readinessLatch = ImmutableSlot<Result<Unit>>()

        val b = Bootstrap()
        b.group(workerGroup)
        b.channel(NioSocketChannel::class.java).option(ChannelOption.SO_KEEPALIVE, true)
        b.handler(object : ChannelInitializer<SocketChannel>() {
            override fun initChannel(ch: SocketChannel) {
                ch.pipeline().addLast(GraphitePlaintextStringEncoder())
            }
        }).option(ChannelOption.SO_KEEPALIVE, true)

        channelFuture = b.connect(host, port).addListener {
            runBlocking {
                if (it.isSuccess) {
                    readinessLatch.set(Result.success(Unit))
                } else {
                    readinessLatch.set(Result.failure(it.cause()))
                }
            }
        }
        log.info { "Graphite connection established. Host: $host, port: $port" }

        readinessLatch.get().getOrThrow()
        started = true
        return this
    }


    suspend fun publish(values: List<String>) {
        val readinessLatch = ImmutableSlot<Result<Unit>>()
        channel.writeAndFlush(values).addListener {
            coroutineScope.launch {
                if (it.isSuccess) {
                    readinessLatch.set(Result.success(Unit))
                } else {
                    readinessLatch.set(Result.failure(it.cause()))
                }
            }
        }
        readinessLatch.get().getOrThrow()
    }

    override suspend fun close() {
        started = false
        channel.closeFuture()
    }

    companion object {

        private val log = logger()
    }
}