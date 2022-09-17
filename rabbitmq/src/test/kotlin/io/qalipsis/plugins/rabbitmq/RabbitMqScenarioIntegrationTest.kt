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
import io.qalipsis.runtime.test.QalipsisTestRunner
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.testcontainers.containers.RabbitMQContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import java.time.Duration
import kotlin.math.pow

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
            factory.password = "defaultpass"
            factory.username = "user"

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

    companion object {

        private const val DOCKER_IMAGE = "rabbitmq:3.8.14-management"

        @Container
        @JvmStatic
        private val container = RabbitMQContainer(DockerImageName.parse(DOCKER_IMAGE))
            .withCreateContainerCmdModifier { it.hostConfig!!.withMemory(256 * 1024.0.pow(2).toLong()).withCpuCount(2) }
            .withEnv("RABBITMQ_VM_MEMORY_HIGH_WATERMARK", "128MiB")
            .withUser("user", "defaultpass", setOf("administrator"))
            .withPermission("/", "user", ".*", ".*", ".*")
    }
}
