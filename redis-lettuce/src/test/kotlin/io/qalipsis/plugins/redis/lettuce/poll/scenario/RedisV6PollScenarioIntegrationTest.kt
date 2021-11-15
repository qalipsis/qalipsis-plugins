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
    fun `should poll hscan with acl`() {
        PollScenario.resetReceivedMessages()
        insertHash("hscan-test-acl", mapOf("alice" to "red", "cris" to "blue"))

        val exitCode = QalipsisTestRunner.withScenarios("lettuce-poll-hscan-with-acl").execute()

        Assertions.assertEquals(0, exitCode)

        assertThat(PollScenario.receivedMessages).all {
            hasSize(2)
            containsExactlyInAnyOrder(
                "alice red",
                "cris blue",
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
                withClasspathResourceMapping("redis-v6.conf", "/etc/redis.conf", BindMode.READ_ONLY)
                withCommand("redis-server /etc/redis.conf")
            }
    }

}
