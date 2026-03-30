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

package io.qalipsis.plugins.jms.consumer

import assertk.all
import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.index
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.prop
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.plugins.jms.Constants
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.relaxedMockk
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.apache.activemq.ActiveMQConnectionFactory
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.RegisterExtension
import org.testcontainers.containers.GenericContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import javax.jms.Connection
import javax.jms.DeliveryMode
import javax.jms.Destination
import javax.jms.MessageProducer
import javax.jms.Session
import javax.jms.TextMessage
import kotlin.math.pow


/**
 *
 * @author Alexander Sosnovsky
 */
@Testcontainers
internal class JmsConsumerIterativeReaderIntegrationTest {

    @JvmField
    @RegisterExtension
    val testDispatcherProvider = TestDispatcherProvider()

    private lateinit var connectionFactory: ActiveMQConnectionFactory

    private lateinit var producerConnection: Connection

    private lateinit var producerSession: Session

    private lateinit var reader: JmsConsumerIterativeReader

    @BeforeAll
    fun initGlobal() {
        connectionFactory = ActiveMQConnectionFactory("tcp://localhost:" + container.getMappedPort(61616))
    }

    @BeforeEach
    fun setUp() {
        producerConnection = connectionFactory.createConnection()
        producerConnection.start()

        producerSession = producerConnection.createSession(false, Session.AUTO_ACKNOWLEDGE)
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
    @Timeout(50)
    internal fun `should consume all the data from subscribed queues only`(): Unit = testDispatcherProvider.run {
        val producer1 = prepareQueueProducer("queue-1")
        val producer2 = prepareQueueProducer("queue-2")
        val producer3 = prepareQueueProducer("queue-3")

        sendMessage("test-queue-message-1", producer1)
        sendMessage("test-queue-message-2", producer1)
        sendMessage("test-queue-message-3", producer2)
        sendMessage("test-queue-message-4", producer3)

        reader = JmsConsumerIterativeReader(
            "any",
            queues = listOf("queue-1", "queue-2"),
            queueConnectionFactory = { connectionFactory.createQueueConnection() },
            topics = listOf(),
            topicConnectionFactory = null
        )

        reader.start(relaxedMockk())


        // when
        val received = mutableListOf<TextMessage>()
        while (received.size < 3) {
            val records = reader.next()
            received.add(records as TextMessage)
        }

        reader.stop(relaxedMockk())

        // then
        assertThat(received).transform { it.sortedBy { it.text } }.all {
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

        producerSession.close()
        producerConnection.close()
    }

    @Test
    @Timeout(50)
    internal fun `should consume all the data from subscribed topics only`(): Unit = testDispatcherProvider.run {
        val producer1 = prepareTopicProducer("topic-1")
        val producer2 = prepareTopicProducer("topic-2")
        val producer3 = prepareTopicProducer("topic-3")

        reader = JmsConsumerIterativeReader(
            "any",
            queues = listOf(),
            queueConnectionFactory = null,
            topics = listOf("topic-1", "topic-3"),
            topicConnectionFactory = { connectionFactory.createTopicConnection() }
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

        reader.stop(relaxedMockk())

        // then
        assertThat(received).transform { it.sortedBy { it.text } }.all {
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

        producerSession.close()
        producerConnection.close()
    }


    @Test
    @Timeout(10)
    internal fun `should always have next at start but not at stop`() = testDispatcherProvider.run {
        reader = JmsConsumerIterativeReader(
            "any",
            queues = listOf(),
            queueConnectionFactory = null,
            topics = listOf("topic-1", "topic-2"),
            topicConnectionFactory = { connectionFactory.createTopicConnection() }
        )

        reader.start(relaxedMockk())
        Assertions.assertTrue(reader.hasNext())

        reader.stop(relaxedMockk())
        Assertions.assertFalse(reader.hasNext())
    }

    @Test
    @Timeout(50)
    internal fun `should accept start after stop and consume`() = testDispatcherProvider.run {
        val producer1 = prepareTopicProducer("topic-1")
        val producer2 = prepareTopicProducer("topic-2")

        reader = JmsConsumerIterativeReader(
            "any",
            queues = listOf(),
            queueConnectionFactory = null,
            topics = listOf("topic-1", "topic-2"),
            topicConnectionFactory = { connectionFactory.createTopicConnection() }
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

        assertThat(received).transform { it.sortedBy { it.text } }.all {
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
        private val container = GenericContainer<Nothing>(Constants.DOCKER_IMAGE).apply {
            withExposedPorts(61616)
            withCreateContainerCmdModifier {
                it.hostConfig!!.withMemory(256 * 1024.0.pow(2).toLong()).withCpuCount(1)
            }
        }

        @JvmStatic
        private val log = logger()
    }

}
