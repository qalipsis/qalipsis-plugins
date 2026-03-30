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

package io.qalipsis.plugins.netty.socket

import io.qalipsis.api.annotations.Spec
import io.qalipsis.api.constraints.PositiveDuration
import io.qalipsis.plugins.netty.configuration.ConnectionConfiguration
import io.qalipsis.plugins.netty.configuration.TlsConfiguration
import io.qalipsis.plugins.netty.tcp.spec.SocketClientPoolConfiguration
import java.time.Duration
import javax.validation.Valid

/**
 *
 * Configuration to connect to a remote peer using a socket.
 *
 * @property connectTimeout time out to establish a connection, defaults to 10 seconds
 * @property keepConnectionAlive keep the connection open even if there is no other step using it (this is required when the connection is used between different operations)
 * @property noDelay disables the Nagle's algorithm when set to `true`, defaults to false
 * @property tlsConfiguration when set, enables and configures TLS to connect to the remote peer
 *
 * @author Eric Jessé
 */
@Spec
abstract class SocketClientConfiguration internal constructor(
    @field:PositiveDuration var connectTimeout: Duration = Duration.ofSeconds(10),
    var keepConnectionAlive: Boolean = false,
    var noDelay: Boolean = true,
    @field:Valid internal var tlsConfiguration: TlsConfiguration? = null
) : ConnectionConfiguration() {

    internal var connectionStrategyConfiguration = ConnectionStrategyConfiguration()

    internal var poolConfiguration: SocketClientPoolConfiguration? = null

    /**
     * Enables and configures the connection to the remote peer with TLS.
     */
    fun tls(configurationBlock: TlsConfiguration.() -> Unit) {
        this.tlsConfiguration = TlsConfiguration()
            .also { it.configurationBlock() }
    }

    /**
     * Configures the connection strategy for this socket connection.
     *
     * - ON_DEMAND: one connection per minion (default)
     * - POOL: shared connection pool
     * - WARMUP: pre-creates all connections at step start
     */
    fun connectionStrategy(configurationBlock: @ConnectionStrategyMarker ConnectionStrategyConfiguration.() -> Unit) {
        connectionStrategyConfiguration.configurationBlock()
    }

    /**
     * Enables the creation of a pool of connections instead of one connection by minion.
     * This optimizes the throughput, when the remote server does not require a unique connection by client or user.
     */
    fun pool(configurationBlock: SocketClientPoolConfiguration.() -> Unit) {
        this.poolConfiguration = SocketClientPoolConfiguration().also { it.configurationBlock() }
    }
}
