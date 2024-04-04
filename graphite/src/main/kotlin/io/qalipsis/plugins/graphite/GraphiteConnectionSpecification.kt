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

package io.qalipsis.plugins.graphite

import io.netty.channel.EventLoopGroup
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioSocketChannel
import io.qalipsis.api.annotations.Spec
import javax.validation.constraints.NotBlank
import javax.validation.constraints.NotNull
import kotlin.reflect.KClass

/**
 * Interface to establish a connection with Graphite
 */
interface GraphiteConnectionSpecification {
    /**
     * Configures the servers settings.
     */
    fun server(host: String, port: Int): GraphiteConnectionSpecification

    /**
     * Configures the netty connection. The concrete types of the channel and the worker group should match.
     * Leave the default value if you are not familiar with those settings.
     */
    fun netty(
        channelClass: KClass<out SocketChannel>,
        workerGroupConfigurer: () -> EventLoopGroup
    ): GraphiteConnectionSpecification

    /**
     * Defines the protocol to use.
     */
    fun protocol(protocol: GraphiteProtocol): GraphiteConnectionSpecification
}

@Spec
internal class GraphiteConnectionSpecificationImpl : GraphiteConnectionSpecification {

    @field:NotBlank
    var host: String = "localhost"

    @field:NotNull
    var port: Int = 2003

    var protocol: GraphiteProtocol = GraphiteProtocol.PLAINTEXT

    var nettyWorkerGroup: () -> EventLoopGroup = { NioEventLoopGroup() }

    var nettyChannelClass: KClass<out SocketChannel> = NioSocketChannel::class

    override fun server(host: String, port: Int): GraphiteConnectionSpecification {
        this.host = host
        this.port = port
        return this
    }

    override fun netty(
        channelClass: KClass<out SocketChannel>,
        workerGroupConfigurer: () -> EventLoopGroup
    ): GraphiteConnectionSpecification {
        this.nettyChannelClass = channelClass
        this.nettyWorkerGroup = workerGroupConfigurer
        return this
    }

    override fun protocol(protocol: GraphiteProtocol): GraphiteConnectionSpecification {
        this.protocol = protocol
        return this
    }
}