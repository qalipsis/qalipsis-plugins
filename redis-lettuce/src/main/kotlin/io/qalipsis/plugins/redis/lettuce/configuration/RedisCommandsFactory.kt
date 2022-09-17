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