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

package io.qalipsis.plugins.redis.lettuce.streams.consumer

import assertk.all
import assertk.assertThat
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.hasSize
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotSameAs
import io.aerisconsulting.catadioptre.getProperty
import io.lettuce.core.RedisClient
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.codec.ByteArrayCodec
import io.lettuce.core.codec.RedisCodec
import io.qalipsis.plugins.redis.lettuce.AbstractRedisIntegrationTest
import io.qalipsis.plugins.redis.lettuce.Constants.REDIS_IMAGE_NAME
import io.qalipsis.test.mockk.relaxedMockk
import kotlinx.coroutines.channels.Channel
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Duration
import java.util.concurrent.CompletionStage
import kotlin.math.pow

@Testcontainers
internal class LettuceStreamsIterativeReaderIntegrationTest : AbstractRedisIntegrationTest(CONTAINER, false) {

    private lateinit var connectionFactory: suspend () -> CompletionStage<out StatefulRedisConnection<ByteArray, ByteArray>>

    @BeforeEach
    fun before() {
        super.setUp()
        connectionFactory = {
            RedisClient.create()
                .connectAsync(RedisCodec.of(ByteArrayCodec.INSTANCE, ByteArrayCodec.INSTANCE), redisURI)
        }
    }

    @Test
    @Timeout(10)
    internal fun `should always have next at start but not at stop`() = testDispatcherProvider.run {
        val reader = LettuceStreamsIterativeReader(
            this,
            testDispatcherProvider.io(),
            connectionFactory,
            "test",
            2,
            "test",
            "0-0",
            relaxedMockk()
        )

        reader.start(relaxedMockk())
        Assertions.assertTrue(reader.hasNext())

        reader.stop(relaxedMockk())
        Assertions.assertFalse(reader.hasNext())
    }

    @Test
    @Timeout(10)
    internal fun `should accept start after stop and consume`() = testDispatcherProvider.run {
        // given
        val topicName = "test-start-stop"
        val reader = LettuceStreamsIterativeReader(
            this,
            testDispatcherProvider.io(),
            connectionFactory,
            "test",
            2,
            topicName,
            "0-0",
            relaxedMockk()
        )

        // when
        reader.start(relaxedMockk())
        val initialChannel = reader.getProperty<Channel<*>>("resultChannel")
        reader.stop(relaxedMockk())
        val valuesInserted = insertValues(topicName, 10)
        reader.start(relaxedMockk())

        // then
        val afterStopStartChannel = reader.getProperty<Channel<*>>("resultChannel")
        val received = mutableListOf<List<String>>()

        while (received.flatten().size < 10) {
            val record = reader.next()

            received.add(record.flatMap { it.body.values }.map { it.decodeToString() })
        }
        reader.stop(relaxedMockk())

        assertThat(afterStopStartChannel).isInstanceOf(Channel::class).isNotSameAs(initialChannel)
        assertThat(received.flatten()).all {
            hasSize(10)
            containsExactlyInAnyOrder(
                *valuesInserted.map { it }.toTypedArray()
            )
        }
    }

    companion object {

        @JvmStatic
        @Container
        private val CONTAINER = GenericContainer<Nothing>(REDIS_IMAGE_NAME)
            .apply {
                withCreateContainerCmdModifier { cmd ->
                    cmd.hostConfig!!.withMemory(50 * 1024.0.pow(2).toLong()).withCpuCount(2)
                }
                withExposedPorts(REDIS_PORT)
                waitingFor(Wait.forListeningPort())
                withStartupTimeout(Duration.ofSeconds(60))
            }
    }
}
