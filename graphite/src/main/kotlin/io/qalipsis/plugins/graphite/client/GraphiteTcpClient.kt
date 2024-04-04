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

package io.qalipsis.plugins.graphite.client

import io.netty.bootstrap.Bootstrap
import io.netty.channel.Channel
import io.netty.channel.ChannelInitializer
import io.netty.channel.ChannelOption
import io.netty.channel.ChannelOutboundHandlerAdapter
import io.netty.channel.EventLoopGroup
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioSocketChannel
import io.qalipsis.api.io.Closeable
import io.qalipsis.api.logging.LoggerHelper.logger
import kotlin.reflect.KClass

/**
 * Client to send records to a Graphite server, using a TCP protocol (Plaintext, pickle).
 *
 * @property host the hostname of the Graphite server
 * @property port the port of the Graphite server
 * @property encoders sorted list of encoders, the first one should be the one that convert a [GraphiteRecord] into the expected Graphite format
 * @property workerGroup worker group to handle the requests, defaults to a [NioEventLoopGroup]
 * @property channelClass class of the channel to open, which should be compliant with the implementation of [workerGroup], defaults to [NioSocketChannel]
 *
 */
internal class GraphiteTcpClient<T : Any>(
    private val host: String,
    private val port: Int,
    private val encoders: List<ChannelOutboundHandlerAdapter>,
    private val workerGroup: EventLoopGroup = NioEventLoopGroup(),
    private val channelClass: KClass<out SocketChannel> = NioSocketChannel::class,
) : Closeable, GraphiteClient<T> {

    private var started = false

    private lateinit var channel: Channel

    override val isOpen: Boolean
        get() = started && channel.isOpen

    override suspend fun open(): GraphiteTcpClient<T> {
        started = false

        val b = Bootstrap()
        b.group(workerGroup)
        b.channel(channelClass.java).option(ChannelOption.SO_KEEPALIVE, true)

        b.handler(object : ChannelInitializer<SocketChannel>() {
            override fun initChannel(ch: SocketChannel) {
                encoders.forEach {
                    ch.pipeline().addLast(it)
                }
            }
        }).option(ChannelOption.SO_KEEPALIVE, true)

        log.debug { "Trying to establish a connection to $host:$port" }
        val latch = kotlinx.coroutines.channels.Channel<Result<Unit>>(1)
        val channelFuture = b.connect(host, port).addListener { result ->
            if (result.isSuccess) {
                log.debug { "Connected to $host:$port" }
                latch.trySend(Result.success(Unit))
            } else {
                log.error(result.cause()) { "The connection to $host:$port could not be established" }
                latch.trySend(Result.failure(result.cause()))
            }
        }

        latch.receive().getOrThrow()

        channel = channelFuture.channel()
        started = true
        return this
    }


    override suspend fun send(values: Collection<T>) {
        val latch = kotlinx.coroutines.channels.Channel<Result<Unit>>(1)
        channel.writeAndFlush(values).addListener { result ->
            if (result.isSuccess) {
                if (log.isTraceEnabled) {
                    log.trace { "The values $values were successfully sent to the Graphite server" }
                } else {
                    log.debug { "The values were successfully sent to the Graphite server" }
                }
                latch.trySend(Result.success(Unit))
            } else {
                log.error(result.cause()) { "The values $values could not be sent to the Graphite server" }
                latch.trySend(Result.failure(result.cause()))
            }
        }
        latch.receive().getOrThrow()
    }

    override suspend fun close() {
        started = false
        log.debug { "Closing the client" }
        workerGroup.shutdownGracefully()
        channel.close()
    }

    companion object {

        private val log = logger()
    }
}