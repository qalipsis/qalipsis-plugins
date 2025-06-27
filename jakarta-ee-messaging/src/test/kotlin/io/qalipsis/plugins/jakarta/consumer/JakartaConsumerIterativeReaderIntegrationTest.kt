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

package io.qalipsis.plugins.jakarta.consumer

import assertk.all
import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.index
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.prop
import io.qalipsis.plugins.jakarta.Constants
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.relaxedMockk
import jakarta.jms.Connection
import jakarta.jms.DeliveryMode
import jakarta.jms.Destination
import jakarta.jms.MessageProducer
import jakarta.jms.Session
import jakarta.jms.TextMessage
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.RegisterExtension
import org.testcontainers.containers.GenericContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import kotlin.math.pow

/**
 * @author Krawist Ngoben
 */
@Testcontainers
internal class JakartaConsumerIterativeReaderIntegrationTest {

    @JvmField
    @RegisterExtension
    val testDispatcherProvider = TestDispatcherProvider()

    private lateinit var connectionFactory: ActiveMQConnectionFactory

    private lateinit var producerConnection: Connection

    private lateinit var producerSession: Session

    private lateinit var reader: JakartaConsumerIterativeReader

    @BeforeAll
    fun initGlobal() {
        connectionFactory = ActiveMQConnectionFactory(
            "tcp://localhost:${container.getMappedPort(61616)}",
            Constants.CONTAINER_USER_NAME,
            Constants.CONTAINER_PASSWORD
        )
    }

    @BeforeEach
    fun setUp() {
        producerConnection = connectionFactory.createConnection()
        producerConnection.start()
        producerSession = producerConnection.createSession(false, Session.AUTO_ACKNOWLEDGE)
    }

    @AfterEach
    fun tearDown() {
        producerSession.close()
        producerConnection.close()
    }

    private fun prepareQueueProducer(queueName: String): MessageProducer {
        val destination: Destination = producerSession.createQueue(queueName)

        val producer = producerSession.createProducer(destination)
        producer.deliveryMode = DeliveryMode.NON_PERSISTENT

        return producer
    }

    private fun prepareTopicProducer(topicName: String): MessageProducer {
        val destination: Destination = producerSession.createTopic(topicName)

        val producer = producerSession.createProducer(destination)
        producer.deliveryMode = DeliveryMode.NON_PERSISTENT

        return producer
    }

    private fun sendMessage(textMessage: String, producer: MessageProducer) {
        val message: TextMessage = producerSession.createTextMessage(textMessage)
        producer.send(message)
    }


    @Test
    @Timeout(20)
    internal fun `should consume all the data from subscribed queues only`(): Unit = testDispatcherProvider.run {
        val producer1 = prepareQueueProducer("queue-1")
        val producer2 = prepareQueueProducer("queue-2")
        val producer3 = prepareQueueProducer("queue-3")

        reader = JakartaConsumerIterativeReader(
            "any",
            queues = listOf("queue-1", "queue-2"),
            queueConnectionFactory = { connectionFactory.createQueueConnection() },
            topics = listOf(),
            topicConnectionFactory = null,
            sessionFactory = Connection::createSession
        )

        reader.start(relaxedMockk())

        delay(4000)

        sendMessage("test-queue-message-1", producer1)
        sendMessage("test-queue-message-2", producer1)
        sendMessage("test-queue-message-3", producer2)
        sendMessage("test-queue-message-4", producer3)

        // when
        val received = mutableListOf<TextMessage>()
        while (received.size < 3) {
            val records = reader.next()
            received.add(records as TextMessage)
        }

        // then
        assertThat(received).transform { it -> it.sortedBy { it.text } }.all {
            hasSize(3)
            index(0).isInstanceOf(TextMessage::class).all {
                transform("text") { it.text }.isEqualTo("test-queue-message-1")
            }
            index(1).isInstanceOf(TextMessage::class).all {
                transform("text") { it.text }.isEqualTo("test-queue-message-2")
            }
            index(2).isInstanceOf(TextMessage::class).all {
                transform("text") { it.text }.isEqualTo("test-queue-message-3")
            }
        }

        //No other message will be read.
        assertThrows<TimeoutCancellationException> {
            withTimeout(1000) {
                reader.next()
            }
        }

        reader.stop(relaxedMockk())
    }

    @Test
    @Timeout(20)
    internal fun `should consume all the data from subscribed topics only`(): Unit = testDispatcherProvider.run {
        val producer1 = prepareTopicProducer("topic-1")
        val producer2 = prepareTopicProducer("topic-2")
        val producer3 = prepareTopicProducer("topic-3")

        reader = JakartaConsumerIterativeReader(
            "any",
            queues = listOf(),
            queueConnectionFactory = null,
            topics = listOf("topic-1", "topic-3"),
            topicConnectionFactory = { connectionFactory.createTopicConnection() },
            sessionFactory = Connection::createSession
        )

        reader.start(relaxedMockk())

        sendMessage("test-topic-message-1", producer1)
        sendMessage("test-topic-message-2", producer1)
        sendMessage("test-topic-message-3", producer2)
        sendMessage("test-topic-message-4", producer3)

        // when
        val received = mutableListOf<TextMessage>()
        while (received.size < 3) {
            val records = reader.next()
            received.add(records as TextMessage)
        }

        // then
        assertThat(received).transform { it -> it.sortedBy { it.text } }.all {
            hasSize(3)
            index(0).all {
                prop(TextMessage::getText).isEqualTo("test-topic-message-1")
            }
            index(1).all {
                prop(TextMessage::getText).isEqualTo("test-topic-message-2")
            }
            index(2).all {
                prop(TextMessage::getText).isEqualTo("test-topic-message-4")
            }
        }

        //No other message will be read.
        assertThrows<TimeoutCancellationException> {
            withTimeout(1000) {
                reader.next()
            }
        }

        reader.stop(relaxedMockk())
    }


    @Test
    @Timeout(10)
    internal fun `should always have next at start but not at stop`() = testDispatcherProvider.run {
        reader = JakartaConsumerIterativeReader(
            "any",
            queues = listOf(),
            queueConnectionFactory = null,
            topics = listOf("topic-1", "topic-2"),
            topicConnectionFactory = { connectionFactory.createTopicConnection() },
            sessionFactory = Connection::createSession
        )

        reader.start(relaxedMockk())
        assertTrue(reader.hasNext())

        reader.stop(relaxedMockk())
        assertFalse(reader.hasNext())
    }

    @Test
    @Timeout(20)
    internal fun `should accept start after stop and consume`() = testDispatcherProvider.run {
        val producer1 = prepareTopicProducer("topic-1")
        val producer2 = prepareTopicProducer("topic-2")

        reader = JakartaConsumerIterativeReader(
            "any",
            queues = listOf(),
            queueConnectionFactory = null,
            topics = listOf("topic-1", "topic-2"),
            topicConnectionFactory = { connectionFactory.createTopicConnection() },
            sessionFactory = Connection::createSession
        )

        reader.start(relaxedMockk())
        reader.stop(relaxedMockk())

        reader.start(relaxedMockk())

        sendMessage("test-startstop-message-1", producer1)
        sendMessage("test-startstop-message-2", producer1)
        sendMessage("test-startstop-message-3", producer2)

        val received = mutableListOf<TextMessage>()

        while (received.size < 3) {
            val record = reader.next()
            received.add(record as TextMessage)
        }

        reader.stop(relaxedMockk())

        assertThat(received).transform { it -> it.sortedBy { it.text } }.all {
            hasSize(3)
            index(0).all {
                prop(TextMessage::getText).isEqualTo("test-startstop-message-1")
            }
            index(1).all {
                prop(TextMessage::getText).isEqualTo("test-startstop-message-2")
            }
            index(2).all {
                prop(TextMessage::getText).isEqualTo("test-startstop-message-3")
            }
        }
    }

    companion object {

        @Container
        @JvmStatic
        val container = GenericContainer<Nothing>(Constants.DOCKER_IMAGE).apply {
            withExposedPorts(61616, 8161)
            withCreateContainerCmdModifier {
                it.hostConfig!!.withMemory(256 * 1024.0.pow(2).toLong()).withCpuCount(1)
            }
            withEnv(Constants.CONTAINER_USER_NAME_ENV_KEY, Constants.CONTAINER_USER_NAME)
            withEnv(Constants.CONTAINER_PASSWORD_ENV_KEY, Constants.CONTAINER_PASSWORD)
        }
    }

}
