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
import assertk.assertions.hasSize
import io.qalipsis.plugins.redis.lettuce.Constants.REDIS_7_DOCKER_IMAGE
import io.qalipsis.runtime.test.QalipsisTestRunner
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.testcontainers.containers.BindMode
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.utility.DockerImageName
import kotlin.math.pow


internal class RedisV7PollScenarioIntegrationTest : AbstractRedisLettucePollScenarioIntegrationTest(CONTAINER) {

    @Test
    @Timeout(20)
    fun `should poll scan with acl`() {
        PollScenario.resetReceivedMessages()
        insertSet("not-allowed-keys-1", "anything")
        insertSet("allowed-keys-1", "alice")
        insertSet("allowed-keys-2", "bob")

        val exitCode = QalipsisTestRunner.withScenarios("poll-scan-with-acl").execute()

        Assertions.assertEquals(0, exitCode)

        assertThat(PollScenario.receivedMessages).all {
            hasSize(2)
            containsExactlyInAnyOrder(
                "allowed-keys-1",
                "allowed-keys-2",
            )
        }
    }

    companion object {

        private val REDIS_IMAGE_NAME: DockerImageName = DockerImageName.parse(REDIS_7_DOCKER_IMAGE)

        @JvmStatic
        @Container
        private val CONTAINER = GenericContainer<Nothing>(REDIS_IMAGE_NAME)
            .apply {
                withCreateContainerCmdModifier { cmd ->
                    cmd.hostConfig!!.withMemory(50 * 1024.0.pow(2).toLong()).withCpuCount(2)
                }
                withExposedPorts(REDIS_PORT)
                waitingFor(Wait.forListeningPort())
                withStartupTimeout(DEFAULT_TIMEOUT)
                withClasspathResourceMapping("redis-v6+.conf", "/etc/redis.conf", BindMode.READ_ONLY)
                withCommand("redis-server /etc/redis.conf")
            }
    }

}
