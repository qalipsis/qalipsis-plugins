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

package io.qalipsis.plugins.redis.lettuce.save

import assertk.assertThat
import assertk.assertions.isEqualTo
import io.qalipsis.plugins.redis.lettuce.AbstractRedisIntegrationTest
import io.qalipsis.plugins.redis.lettuce.Constants.REDIS_IMAGE_NAME
import io.qalipsis.runtime.test.QalipsisTestRunner
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.junit.jupiter.Container
import java.time.Duration
import kotlin.math.pow

internal class RedisLettuceSaveScenarioIntegrationTest : AbstractRedisIntegrationTest(CONTAINER, false) {

    @BeforeAll
    fun beforeAll() {
        SaveScenario.dbNodes = listOf("${CONTAINER.host}:${CONTAINER.getMappedPort(REDIS_PORT)}")
    }

    @BeforeEach
    fun beforeEach() {
        super.setUp()
    }

    @Test
    @Timeout(20)
    fun `should be able to save key and value in redis`() {
        insertValues("test")

        val exitCode = QalipsisTestRunner.withScenarios("lettuce-save-record").execute()
        Assertions.assertEquals(0, exitCode)

        val valueMessage = connection.sync().get("value".toByteArray()).decodeToString()
        val hashMessage = connection.sync().hget("hash".toByteArray(), "test".toByteArray()).decodeToString()

        assertThat(valueMessage).isEqualTo("test")
        assertThat(hashMessage).isEqualTo("test1")
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
