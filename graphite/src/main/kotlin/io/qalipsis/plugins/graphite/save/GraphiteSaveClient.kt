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

    private val channel: Channel
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


    suspend fun publish(values: List<GraphiteRecord>) {
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