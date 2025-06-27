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

package io.qalipsis.plugins.redis.lettuce.configuration

import io.lettuce.core.api.StatefulConnection
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.cluster.api.StatefulRedisClusterConnection
import io.lettuce.core.cluster.api.async.RedisClusterAsyncCommands

/**
 * Factory to get redis async command from any type of [StatefulConnection].
 */
internal object RedisCommandsFactory {

    /**
     * Gets an async command from a [connection].
     */
    fun getAsyncCommand(connection: StatefulConnection<ByteArray, ByteArray>): RedisClusterAsyncCommands<ByteArray, ByteArray> {
        return when (connection) {
            is StatefulRedisClusterConnection -> connection.async()
            is StatefulRedisConnection -> connection.async()
            else -> throw IllegalStateException("Connection type is not implemented to perform redis commands")
        }
    }
}