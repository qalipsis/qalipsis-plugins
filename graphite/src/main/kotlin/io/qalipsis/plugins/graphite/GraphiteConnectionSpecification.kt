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