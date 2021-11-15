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