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

package io.qalipsis.plugins.redis.lettuce

import io.lettuce.core.LettuceFutures
import io.lettuce.core.RedisClient
import io.lettuce.core.RedisURI
import io.lettuce.core.ScoredValue
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.codec.ByteArrayCodec
import io.lettuce.core.codec.RedisCodec
import io.qalipsis.test.coroutines.TestDispatcherProvider
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.extension.RegisterExtension
import org.testcontainers.containers.GenericContainer
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Duration
import java.util.concurrent.Future

@Testcontainers
internal abstract class AbstractRedisIntegrationTest(
    private val container: GenericContainer<Nothing>,
    private val hasPassword: Boolean = true
) {

    @JvmField
    @RegisterExtension
    val testDispatcherProvider = TestDispatcherProvider()

    protected lateinit var client: RedisClient

    protected lateinit var connection: StatefulRedisConnection<ByteArray, ByteArray>

    protected lateinit var redisURI: RedisURI

    open fun setUp() {
        redisURI =
            RedisURI.create("${RedisURI.URI_SCHEME_REDIS}://${container.host}:${container.getMappedPort(REDIS_PORT)}")
        if (hasPassword) {
            redisURI.password = REDIS_PASS.toCharArray()
        }
        client = RedisClient.create()
        connection = client.connect(RedisCodec.of(ByteArrayCodec.INSTANCE, ByteArrayCodec.INSTANCE), redisURI)
    }

    @AfterEach
    fun afterEach() {
        connection.sync().flushdb()
        connection.close()
        client.shutdown()
    }

    protected fun insertValues(name: String, numberItems: Int = 30): List<String> {
        val futures = mutableListOf<Future<*>>()
        val values = (1..numberItems).map { "B$it" }
        connection.async().let { conn ->
            values.forEach {
                futures.add(conn.xadd(name.toByteArray(), mapOf("foo".toByteArray() to it.toByteArray())))
            }
        }
        LettuceFutures.awaitAll(DEFAULT_TIMEOUT, *futures.toTypedArray())
        return values
    }

    fun insertSet(key: String, vararg values: String) {
        connection.sync().sadd(key.toByteArray(), *values.map { it.toByteArray() }.toTypedArray())
    }

    fun insertKeyValue(key: String, value: String) {
        connection.sync().set(key.toByteArray(), value.toByteArray())
    }

    fun insertHash(key: String, value: Map<String, String>) {
        val byteArrayMap = value.mapKeys { it.key.toByteArray() }.mapValues { it.value.toByteArray() }
        connection.sync().hset(key.toByteArray(), byteArrayMap)
    }

    fun insertSortedSet(key: String, vararg values: ScoredValue<String>) {
        connection.sync()
            .zadd(
                key.toByteArray(),
                *values.map { ScoredValue.fromNullable(it.score, it.value.toByteArray()) }.toTypedArray()
            )
    }

    companion object {
        const val REDIS_PORT = 6379
        const val REDIS_PASS = "nUwUvH"
        internal val DEFAULT_TIMEOUT = Duration.ofSeconds(10)
    }

}
