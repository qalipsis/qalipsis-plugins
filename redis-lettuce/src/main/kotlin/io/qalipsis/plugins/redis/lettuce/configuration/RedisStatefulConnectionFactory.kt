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

import io.lettuce.core.ConnectionFuture
import io.lettuce.core.RedisClient
import io.lettuce.core.RedisURI
import io.lettuce.core.api.StatefulConnection
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.cluster.RedisClusterClient
import io.lettuce.core.cluster.api.StatefulRedisClusterConnection
import io.lettuce.core.codec.ByteArrayCodec
import io.lettuce.core.codec.RedisCodec
import io.qalipsis.api.sync.asSuspended
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage

/**
 * Factory for a redis connection using the configuration defined by the client in the [RedisConnectionConfiguration].
 */
internal class RedisStatefulConnectionFactory(private val connectionRedis: RedisConnectionConfiguration) {

    /**
     * Creates a redis connection based in the [RedisConnectionType] defined in the specification of the step.
     */
    internal suspend fun create(): CompletionStage<out StatefulConnection<ByteArray, ByteArray>> {
        return when (connectionRedis.redisConnectionType) {
            RedisConnectionType.CLUSTER -> redisClusterConnectionBuilder(connectionRedis)
            RedisConnectionType.SINGLE -> redisSingleConnectionBuilder(connectionRedis)
            RedisConnectionType.SENTINEL -> redisSentinelConnectionBuilder(connectionRedis)
        }

    }

    /**
     * Creates a [ConnectionFuture] to a redis with one node.
     */
    private fun redisSingleConnectionBuilder(
        connectionRedis: RedisConnectionConfiguration
    ): ConnectionFuture<StatefulRedisConnection<ByteArray, ByteArray>> {
        val redisUriBuilder = createRedisURIBuilder(connectionRedis, connectionRedis.nodes.first())
        redisUriBuilder.withDatabase(connectionRedis.database)
        return RedisClient.create()
            .connectAsync(RedisCodec.of(ByteArrayCodec.INSTANCE, ByteArrayCodec.INSTANCE), redisUriBuilder.build())
    }

    /**
     * Creates a [ConnectionFuture] to a redis with sentinel.
     * To learn more about redis sentinel, see [here](https://redis.io/topics/sentinel).
     */
    private fun redisSentinelConnectionBuilder(
        connectionRedis: RedisConnectionConfiguration
    ): ConnectionFuture<StatefulRedisConnection<ByteArray, ByteArray>> {

        val redisURI = RedisURI.builder()
        val connectionSplitted = connectionRedis.nodes.first().split(":")
        val port =
            connectionSplitted.getOrNull(1) ?: throw IllegalStateException(
                "Connection node must have a port defined"
            )

        if (connectionRedis.authPassword.isNotBlank()) {
            redisURI.withSentinel(connectionSplitted[0], port.toInt(), connectionRedis.authPassword)
        } else {
            redisURI.withSentinel(connectionSplitted[0], port.toInt())
        }

        require(connectionRedis.masterId.isNotBlank())

        redisURI.withSentinelMasterId(connectionRedis.masterId)
        redisURI.withDatabase(connectionRedis.database)

        return RedisClient.create()
            .connectAsync(RedisCodec.of(ByteArrayCodec.INSTANCE, ByteArrayCodec.INSTANCE), redisURI.build())
    }

    /**
     * Creates a [CompletableFuture] to a redis in a cluster configuration.
     */
    private suspend fun redisClusterConnectionBuilder(
        connectionRedis: RedisConnectionConfiguration
    ): CompletableFuture<StatefulRedisClusterConnection<ByteArray, ByteArray>> {
        val redisUris = connectionRedis.nodes.map {
            createRedisURIBuilder(connectionRedis, it).build()
        }
        val clusterClient = RedisClusterClient.create(redisUris)
        clusterClient.refreshPartitionsAsync().asSuspended().get(DEFAULT_TIMEOUT)

        return clusterClient.connectAsync(RedisCodec.of(ByteArrayCodec.INSTANCE, ByteArrayCodec.INSTANCE))
    }

    private fun createRedisURIBuilder(
        connectionRedis: RedisConnectionConfiguration,
        nodeConnection: String
    ): RedisURI.Builder {
        val connectionSplitted = nodeConnection.split(":")
        val port =
            connectionSplitted.getOrNull(1) ?: throw IllegalStateException("Connection node must have a port defined")

        val redisUri = RedisURI.builder()
            .withHost(connectionSplitted[0])
            .withPort(port.toInt())

        if (connectionRedis.authPassword.isNotBlank()) {
            redisUri.withAuthentication(connectionRedis.authUser, connectionRedis.authPassword.toCharArray())
        }

        return redisUri
    }

    /**
     * Timeout used by default when connecting to redis.
     */
    private val DEFAULT_TIMEOUT = Duration.ofSeconds(10)
}
