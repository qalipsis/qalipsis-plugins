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
