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

package io.qalipsis.plugins.rabbitmq

import assertk.all
import assertk.assertThat
import assertk.assertions.containsOnly
import assertk.assertions.hasSize
import com.fasterxml.jackson.databind.ObjectMapper
import com.rabbitmq.client.Channel
import com.rabbitmq.client.Connection
import com.rabbitmq.client.ConnectionFactory
import com.rabbitmq.client.MessageProperties
import io.qalipsis.plugins.rabbitmq.Constants.DOCKER_IMAGE
import io.qalipsis.runtime.test.QalipsisTestRunner
import java.time.Duration
import kotlin.math.pow
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.testcontainers.containers.RabbitMQContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName

/**
 * @author Gabriel Moraes
 */
@Testcontainers
internal class RabbitMqScenarioIntegrationTest {

    private lateinit var connection: Connection

    private var initialized = false

    @BeforeEach
    internal fun setUp() {
        if (!initialized) {
            val factory = ConnectionFactory()
            factory.host = container.host
            factory.port = container.amqpPort
            factory.username = "the-user"
            factory.password = "the-password"

            connection = factory.newConnection()

            RabbitMqScenario.portContainer = container.amqpPort
            RabbitMqScenario.hostContainer = container.host

            initialized = true
        }
    }

    @AfterAll
    internal fun tearDown() {
        connection.close(Duration.ofSeconds(30).toMillis().toInt())
        initialized = false
    }

    private fun createExchangeAndQueue(channel: Channel, queueName: String, type: String = "direct") {
        channel.exchangeDeclare(queueName, type, true)

        val queue = channel.queueDeclare(queueName, true, false, true, emptyMap()).queue
        channel.queueBind(queue, queueName, queueName)
    }

    @Test
    @Timeout(20)
    internal fun `should run the consumer scenario`() {
        val channel = connection.createChannel()

        val queueName = "user"
        createExchangeAndQueue(channel, queueName, "fanout")

        val secondQueue = channel.queueDeclare("user-deserializer", true, false, true, emptyMap()).queue
        channel.queueBind(secondQueue, queueName, "")

        var body = ObjectMapper().writeValueAsBytes(RabbitMqScenario.User("10"))
        channel.basicPublish(queueName, "", MessageProperties.PERSISTENT_TEXT_PLAIN, body)

        body = ObjectMapper().writeValueAsBytes(RabbitMqScenario.User("20"))
        channel.basicPublish(queueName, "", MessageProperties.PERSISTENT_TEXT_PLAIN, body)

        channel.close()

        RabbitMqScenario.receivedMessages.clear()
        val exitCode = QalipsisTestRunner.withScenarios("consumer-rabbitmq").execute()

        Assertions.assertEquals(0, exitCode)
        assertThat(RabbitMqScenario.receivedMessages).all {
            hasSize(RabbitMqScenario.minions)
            containsOnly("10", "20")
        }
    }

    @Test
    @Timeout(20)
    internal fun `should run the consumer scenario with string deserializer`() {
        val channel = connection.createChannel()

        val queueName = "string-deserializer"
        createExchangeAndQueue(channel, queueName)

        channel.basicPublish(queueName, queueName, MessageProperties.PERSISTENT_TEXT_PLAIN, "rabbitmq".toByteArray())
        channel.basicPublish(queueName, queueName, MessageProperties.PERSISTENT_TEXT_PLAIN, "rabbitmq2".toByteArray())

        channel.close()

        RabbitMqScenario.receivedMessages.clear()
        val exitCode = QalipsisTestRunner.withScenarios("consumer-rabbitmq-string-deserializer").execute()

        Assertions.assertEquals(0, exitCode)
        assertThat(RabbitMqScenario.receivedMessages).all {
            hasSize(RabbitMqScenario.minions)
            containsOnly("rabbitmq", "rabbitmq2")
        }
    }

    @Test
    @Timeout(20)
    internal fun `should run the consumer scenario with defaults`() {
        val channel = connection.createChannel()

        val queueName = "string-deserializer-defaults"
        createExchangeAndQueue(channel, queueName)

        channel.basicPublish(queueName, queueName, MessageProperties.PERSISTENT_TEXT_PLAIN, "rabbitmq-d1".toByteArray())
        channel.basicPublish(queueName, queueName, MessageProperties.PERSISTENT_TEXT_PLAIN, "rabbitmq-d2".toByteArray())

        channel.close()

        RabbitMqScenario.receivedMessages.clear()
        val exitCode = QalipsisTestRunner.withScenarios("consumer-rabbitmq-with-defaults").execute()

        Assertions.assertEquals(0, exitCode)
        assertThat(RabbitMqScenario.receivedMessages).all {
            hasSize(RabbitMqScenario.minions)
            containsOnly("rabbitmq-d1", "rabbitmq-d2")
        }
    }

    companion object {

        @Container
        @JvmStatic
        private val container = RabbitMQContainer(DockerImageName.parse(DOCKER_IMAGE))
            .withCreateContainerCmdModifier { it.hostConfig!!.withMemory(256 * 1024.0.pow(2).toLong()).withCpuCount(2) }
            .withAdminUser("the-user")
            .withAdminPassword("the-password")
    }
}
