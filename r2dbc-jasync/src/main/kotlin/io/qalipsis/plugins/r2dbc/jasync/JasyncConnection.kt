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

package io.qalipsis.plugins.r2dbc.jasync

import com.github.jasync.sql.db.SSLConfiguration
import com.github.jasync.sql.db.interceptor.QueryInterceptor
import com.github.jasync.sql.db.util.ExecutorServiceUtils
import com.github.jasync.sql.db.util.NettyUtils
import io.netty.buffer.ByteBufAllocator
import io.netty.buffer.PooledByteBufAllocator
import io.netty.channel.EventLoopGroup
import io.qalipsis.api.annotations.Spec
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.concurrent.Executor
import java.util.function.Supplier
import javax.validation.constraints.NotBlank
import javax.validation.constraints.Positive

/**
 * Connection for a any operation using jasync and a pool of connection.
 *
 * @property host database host, defaults to "localhost".
 * @property port database port.
 * @property database database name, defaults to no database.
 * @property username database username.
 * @property password password, defaults to no password.
 * @property maxActiveConnections the maximum count of active connections.
 * @property maxIdleTime duration for which the objects are going to be kept as idle (not in use by clients of the pool.
 * @property maxPendingQueries when there are no more objects, the pool can queue up requests to serve later then there
 * are objects available, this is the maximum number of enqueued requests.
 * @property connectionValidationInterval pools will use this value as the timer period to validate idle objects.
 * @property connectionCreateTimeout the timeout for connecting to servers.
 * @property connectionTestTimeout the timeout for connection tests performed by pools.
 * @property ssl ssl configuration.
 * @property charset charset for the connection, defaults to UTF-8, make sure you know what you are doing if you
 *                change this.
 * @property maximumMessageSize the maximum size a message from the server could possibly have, this limits possible
 *                           OOM or eternal loop attacks the client could have, defaults to 16 MB. You can set this
 *                           to any value you would like but again, make sure you know what you are doing if you do
 *                           change it.
 * @property queryTimeout the optional query timeout.
 * @property executionContext the thread pool to run the callbacks on.
 * @property eventLoopGroup the netty event loop group - use this to select native/nio transport.
 * @property applicationName optional name to be passed to the database for reporting.
 * @property interceptors optional delegates to call on query execution.
 * @property maxConnectionTtl duration for an object in this pool should be kept alive.
 */
@Spec
data class JasyncConnection internal constructor(
    @field:NotBlank var host: String = "localhost",
    @field:Positive var port: Int = -1,
    @field:NotBlank var database: String? = null,
    @field:NotBlank var username: String? = null,
    var password: String? = null,
    @field:Positive var maxActiveConnections: Int = 1,
    @field:Positive var maxIdleTime: Duration = Duration.ofMinutes(1),
    @field:Positive var maxPendingQueries: Int = Int.MAX_VALUE,
    @field:Positive var connectionValidationInterval: Duration = Duration.ofMillis(5000),
    @field:Positive var connectionCreateTimeout: Duration = Duration.ofMillis(5000),
    @field:Positive var connectionTestTimeout: Duration = Duration.ofMillis(5000),
    @field:Positive var queryTimeout: Duration? = null,
    var eventLoopGroup: EventLoopGroup = NettyUtils.DefaultEventLoopGroup,
    var executionContext: Executor = ExecutorServiceUtils.CommonPool,
    var ssl: SSLConfiguration = SSLConfiguration(),
    var charset: Charset = StandardCharsets.UTF_8,
    @field:Positive var maximumMessageSize: Int = 16777216,
    var allocator: ByteBufAllocator = PooledByteBufAllocator.DEFAULT,
    var applicationName: String? = "r2dbc-jasync",
    var interceptors: List<Supplier<QueryInterceptor>> = emptyList(),
    var maxConnectionTtl: Duration? = null
)


