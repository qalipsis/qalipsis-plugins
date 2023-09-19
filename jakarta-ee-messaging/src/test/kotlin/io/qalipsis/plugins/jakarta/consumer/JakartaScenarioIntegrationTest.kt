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

package io.qalipsis.plugins.jakarta.consumer

import assertk.assertThat
import assertk.assertions.containsOnly
import com.fasterxml.jackson.databind.ObjectMapper
import io.qalipsis.plugins.jakarta.Constants
import io.qalipsis.runtime.test.QalipsisTestRunner
import jakarta.jms.Connection
import jakarta.jms.DeliveryMode
import jakarta.jms.Destination
import jakarta.jms.MessageProducer
import jakarta.jms.Session
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.testcontainers.containers.GenericContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import kotlin.math.pow


/**
 * @author Krawist Ngoben
 */
@Testcontainers
internal class JakartaScenarioIntegrationTest {
    private lateinit var producerConnection: Connection

    private lateinit var producerSession: Session

    private lateinit var queueConnectionFactory: ActiveMQConnectionFactory

    @BeforeEach
    internal fun setUp() {
        queueConnectionFactory = ActiveMQConnectionFactory(
            "tcp://localhost:${container.getMappedPort(61616)}",
            Constants.CONTAINER_USER_NAME,
            Constants.CONTAINER_PASSWORD
        )

        producerConnection = queueConnectionFactory.createConnection()

        producerConnection.start()

        producerSession = producerConnection.createSession()

        JakartaScenario.queueConnectionFactory = queueConnectionFactory
    }

    @AfterEach
    internal fun tearDown() {
        producerSession.close()
        producerConnection.close()
    }


    @Test
    @Timeout(50)
    internal fun `should run the consumer scenario`() {
        val producer1 = prepareQueueProducer("queue-1")
        val producer2 = prepareQueueProducer("queue-2")
        val objectMapper = ObjectMapper()
        sendMessage(objectMapper.writeValueAsString(JakartaScenario.User("10", "alex")), producer1)
        sendMessage(objectMapper.writeValueAsString(JakartaScenario.User("20", "bob")), producer1)
        sendMessage(objectMapper.writeValueAsString(JakartaScenario.User("10", "charly")), producer2)
        sendMessage(objectMapper.writeValueAsString(JakartaScenario.User("20", "david")), producer2)

        JakartaScenario.receivedMessages.clear()
        val exitCode = QalipsisTestRunner.withScenarios("consumer-jakarta").execute()
        assertEquals(0, exitCode)

        assertThat(
            listOf(
                JakartaScenario.receivedMessages.poll(),
                JakartaScenario.receivedMessages.poll()
            )
        ).containsOnly("10", "20")
    }

    @Test
    @Timeout(50)
    internal fun `should run the consumer scenario with string deserializer`() {
        val producer = prepareQueueProducer("queue-3")
        sendMessage("jakarta", producer)
        sendMessage("jakarta2", producer)

        JakartaScenario.receivedMessages.clear()
        val exitCode = QalipsisTestRunner.withScenarios("consumer-jakarta-string-deserializer").execute()

        assertEquals(0, exitCode)
        assertThat(
            listOf(
                JakartaScenario.receivedMessages.poll(),
                JakartaScenario.receivedMessages.poll()
            )
        ).containsOnly("jakarta", "jakarta2")
    }

    private fun prepareQueueProducer(queueName: String): MessageProducer {
        val destination: Destination = producerSession.createQueue(queueName)

        val producer = producerSession.createProducer(destination)
        producer.deliveryMode = DeliveryMode.NON_PERSISTENT

        return producer
    }

    /**
     * Use a byte message for testing purpose, because the conversion cannot be unit tested.
     */
    private fun sendMessage(textMessage: String, producer: MessageProducer) {
        val message = producerSession.createBytesMessage()
        message.writeBytes(textMessage.toByteArray())
        producer.send(message)
    }

    companion object {

        @Container
        @JvmStatic
        val container = GenericContainer<Nothing>(Constants.DOCKER_IMAGE).apply {
            withExposedPorts(61616)
            withCreateContainerCmdModifier {
                it.hostConfig!!.withMemory(256 * 1024.0.pow(2).toLong()).withCpuCount(1)
            }
            withEnv(Constants.CONTAINER_USER_NAME_ENV_KEY, Constants.CONTAINER_USER_NAME)
            withEnv(Constants.CONTAINER_PASSWORD_ENV_KEY, Constants.CONTAINER_PASSWORD)
        }
    }
}
