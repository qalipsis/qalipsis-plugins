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

package io.qalipsis.plugins.redis.lettuce.poll.scenario

import assertk.all
import assertk.assertThat
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.hasSize
import io.lettuce.core.RedisClient
import io.lettuce.core.RedisURI
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.codec.ByteArrayCodec
import io.lettuce.core.codec.RedisCodec
import io.qalipsis.plugins.redis.lettuce.AbstractRedisIntegrationTest
import io.qalipsis.runtime.test.QalipsisTestRunner
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.condition.DisabledOnOs
import org.junit.jupiter.api.condition.OS
import org.testcontainers.containers.DockerComposeContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.io.File
import java.util.concurrent.TimeUnit

@Testcontainers
@DisabledOnOs(value = [OS.MAC]) // Docker on Mac does not support bridge networking in Docker.
internal class RedisSentinelLettucePollScenarioIntegrationTest {

    private lateinit var redisClient: RedisClient

    private lateinit var connection: StatefulRedisConnection<ByteArray, ByteArray>

    @BeforeEach
    fun setUp() {
        val host = CONTAINER.getServiceHost(SENTINEL, SENTINEL_PORT)
        val port = CONTAINER.getServicePort(SENTINEL, SENTINEL_PORT)
        val redisUri = RedisURI.builder()
            .withSentinel(host, port)
            .withSentinelMasterId("mymaster")

        redisClient = RedisClient.create(redisUri.build())

        PollScenario.dbNodes = listOf("$host:$port")
        connection = redisClient
            .connectAsync(RedisCodec.of(ByteArrayCodec.INSTANCE, ByteArrayCodec.INSTANCE), redisUri.build())
            .get(AbstractRedisIntegrationTest.DEFAULT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)
    }

    @Test
    @Timeout(20)
    fun `should be able to poll redis sscan in sentinel mode`() {
        PollScenario.resetReceivedMessages()
        val setList = insertSet()

        val exitCode = QalipsisTestRunner.withScenarios("poll-sscan-sentinel").execute()

        Assertions.assertEquals(0, exitCode)

        assertThat(PollScenario.receivedMessages).all {
            hasSize(30)
            containsExactlyInAnyOrder(
                *setList.map { it }.toTypedArray()
            )
        }
    }

    private fun insertSet(): List<String> {
        val values = (1..30).map { "foo$it" }
        connection.sync().sadd("A".toByteArray(), *values.map { it.toByteArray() }.toTypedArray())
        return values
    }

    companion object {

        private const val SENTINEL = "sentinel"
        private const val SENTINEL_PORT = 26379

        @JvmStatic
        @Container
        private val CONTAINER = DockerComposeContainer<Nothing>(File("src/test/resources/docker-compose.yml"))
            .apply {
                withExposedService(SENTINEL, SENTINEL_PORT, Wait.forListeningPort())
            }
    }
}
