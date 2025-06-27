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
import io.qalipsis.plugins.redis.lettuce.Constants.REDIS_IMAGE_NAME
import io.qalipsis.runtime.test.QalipsisTestRunner
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.testcontainers.containers.BindMode
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.junit.jupiter.Container
import java.time.Duration
import kotlin.math.pow


internal class RedisV6PollScenarioIntegrationTest : AbstractRedisLettucePollScenarioIntegrationTest(CONTAINER) {

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
                withClasspathResourceMapping("redis-v6+.conf", "/etc/redis.conf", BindMode.READ_ONLY)
                withCommand("redis-server /etc/redis.conf")
            }
    }

}
