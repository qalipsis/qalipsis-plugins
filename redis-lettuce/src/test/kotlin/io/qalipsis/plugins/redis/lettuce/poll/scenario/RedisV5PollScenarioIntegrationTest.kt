package io.qalipsis.plugins.redis.lettuce.poll.scenario

import io.qalipsis.plugins.redis.lettuce.Constants.REDIS_5_DOCKER_IMAGE
import org.testcontainers.containers.BindMode
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.utility.DockerImageName
import java.time.Duration
import kotlin.math.pow


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
