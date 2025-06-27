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

import io.qalipsis.plugins.redis.lettuce.Constants.REDIS_5_DOCKER_IMAGE
import org.junit.jupiter.api.Disabled
import org.testcontainers.containers.BindMode
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.utility.DockerImageName
import java.time.Duration
import kotlin.math.pow

// FIXME Test working with Redis 6 but not Redis 5
@Disabled("FIXME: Test working with Redis 6 but not Redis 5")
internal class RedisV5PollScenarioIntegrationTest : AbstractRedisLettucePollScenarioIntegrationTest(CONTAINER) {

    companion object {
        @JvmStatic
        private val IMAGE_NAME: DockerImageName = DockerImageName.parse(REDIS_5_DOCKER_IMAGE)

        @JvmStatic
        @Container
        private val CONTAINER = GenericContainer<Nothing>(IMAGE_NAME)
            .apply {
                withCreateContainerCmdModifier { cmd ->
                    cmd.hostConfig!!.withMemory(50 * 1024.0.pow(2).toLong()).withCpuCount(2)
                }
                withExposedPorts(REDIS_PORT)
                waitingFor(Wait.forListeningPort())
                withStartupTimeout(Duration.ofSeconds(60))
                withClasspathResourceMapping("redis-v5.conf", "/etc/redis.conf", BindMode.READ_ONLY)
                withCommand("redis-server /etc/redis.conf")
            }
    }

}
