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

package io.qalipsis.plugins.redis.lettuce.poll.scenario

import assertk.all
import assertk.assertThat
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.hasSameSizeAs
import io.lettuce.core.RedisURI
import io.lettuce.core.cluster.RedisClusterClient
import io.lettuce.core.cluster.api.StatefulRedisClusterConnection
import io.lettuce.core.codec.ByteArrayCodec
import io.lettuce.core.codec.RedisCodec
import io.qalipsis.plugins.redis.lettuce.Constants.REDIS_CLUSTER_IMAGE_NAME
import io.qalipsis.runtime.test.QalipsisTestRunner
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.condition.DisabledOnOs
import org.junit.jupiter.api.condition.OS
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Duration
import kotlin.math.pow

@Testcontainers
@DisabledOnOs(value = [OS.MAC]) // Docker on Mac does not support the required networking configuration.
internal class RedisClusterLettucePollScenarioIntegrationTest {

    lateinit var redisClusterClient: RedisClusterClient

    lateinit var connection: StatefulRedisClusterConnection<ByteArray, ByteArray>

    @BeforeEach
    fun setUp() {
        val redisUri = RedisURI.builder()
            .withHost(CONTAINER.host)
            .withPort(CONTAINER.getMappedPort(7000))
        redisClusterClient = RedisClusterClient.create(redisUri.build())
        redisClusterClient.refreshPartitions()
        connection = redisClusterClient.connect(RedisCodec.of(ByteArrayCodec.INSTANCE, ByteArrayCodec.INSTANCE))
        while (!connection.sync().clusterInfo().contains("cluster_state:ok")) {
            Thread.sleep(1000)
        }

        PollScenario.dbNodes = listOf(
            "${CONTAINER.host}:${CONTAINER.getMappedPort(7000)}",
            "${CONTAINER.host}:${CONTAINER.getMappedPort(7001)}"
        )
    }

    @Test
    @Timeout(20)
    fun `should be able to poll redis scan in cluster`() {
        PollScenario.resetReceivedMessages()
        val keys = insertClusterKeys()

        val exitCode = QalipsisTestRunner.withScenarios("lettuce-poll-scan-cluster").execute()

        Assertions.assertEquals(0, exitCode)

        assertThat(PollScenario.receivedMessages).all {
            hasSameSizeAs(keys)
            containsExactlyInAnyOrder(
                *keys.map { it }.toTypedArray()
            )
        }
    }

    @Test
    @Timeout(20)
    fun `should be able to poll redis scan in cluster batch`() {
        PollScenario.resetReceivedMessages()
        val keys = insertClusterKeys()

        val exitCode = QalipsisTestRunner.withScenarios("lettuce-poll-scan-cluster-batch").execute()

        Assertions.assertEquals(0, exitCode)

        assertThat(PollScenario.receivedMessages).all {
            hasSameSizeAs(keys)
            containsExactlyInAnyOrder(
                *keys.map { it }.toTypedArray()
            )
        }
    }

    private fun insertClusterKeys(): List<String> {
        connection.partitions.updateCache()
        val keys = (1..10).map { "A$it" }
        keys.forEach {
            var success = false
            while (!success) {
                try {
                    connection.sync().set(it.toByteArray(), "foo".toByteArray())
                    success = true
                } catch (e: Exception) {
                    connection.partitions.updateCache()
                }
            }
        }
        return keys
    }

    companion object {

        @JvmStatic
        @Container
        private val CONTAINER = GenericContainer<Nothing>(REDIS_CLUSTER_IMAGE_NAME)
            .apply {
                withCreateContainerCmdModifier { cmd ->
                    cmd.hostConfig!!.withMemory(100 * 1024.0.pow(2).toLong()).withCpuCount(2)
                }
                withExposedPorts(7000, 7001, 7002)
                waitingFor(Wait.forLogMessage(".*Background AOF rewrite finished successfully.*", 1))
                withStartupTimeout(Duration.ofSeconds(60))
            }
    }
}
