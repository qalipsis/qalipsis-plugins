package io.qalipsis.plugins.redis.lettuce.streams.producer

import assertk.all
import assertk.assertThat
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.hasSameSizeAs
import io.lettuce.core.XReadArgs
import io.qalipsis.plugins.redis.lettuce.AbstractRedisIntegrationTest
import io.qalipsis.plugins.redis.lettuce.Constants.REDIS_IMAGE_NAME
import io.qalipsis.plugins.redis.lettuce.streams.consumer.ConsumerScenario
import io.qalipsis.runtime.test.QalipsisTestRunner
import kotlinx.coroutines.runBlocking
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

internal class RedisLettuceProducerScenarioIntegrationTest : AbstractRedisIntegrationTest(CONTAINER, false) {

    @BeforeAll
    fun beforeAll() {
        ProducerScenario.dbNodes = listOf(
            "${CONTAINER.host}:${CONTAINER.getMappedPort(REDIS_PORT)}"
        )
    }

    @BeforeEach
    fun beforeEach() = runBlocking {
        super.setUp()
    }

    @Test
    @Timeout(20)
    fun `should be able to produce in redis`() {
        ConsumerScenario.receivedMessages.clear()
        val valuesInserted = insertValues("test")

        val exitCode = QalipsisTestRunner.withScenarios("lettuce-producer").execute()

        Assertions.assertEquals(0, exitCode)
        val messagesReceived = mutableListOf<String>()

        while (messagesReceived.size != 30) {
            val messages = connection.sync().xread(XReadArgs.StreamOffset.from("producer.test".toByteArray(), "0"))
            messages.forEach {
                messagesReceived.add(it.body.values.first().decodeToString())
            }
        }

        assertThat(messagesReceived).all {
            hasSameSizeAs(valuesInserted)
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
