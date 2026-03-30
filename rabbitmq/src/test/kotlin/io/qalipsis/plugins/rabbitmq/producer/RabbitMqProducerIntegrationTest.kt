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

package io.qalipsis.plugins.rabbitmq.producer

import assertk.all
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.prop
import com.rabbitmq.client.CancelCallback
import com.rabbitmq.client.Channel
import com.rabbitmq.client.ConnectionFactory
import com.rabbitmq.client.DeliverCallback
import com.rabbitmq.client.Delivery
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.slot
import io.qalipsis.api.meters.Counter
import io.qalipsis.plugins.rabbitmq.Constants.DOCKER_IMAGE
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.WithMockk
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.extension.RegisterExtension
import org.testcontainers.containers.RabbitMQContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import java.util.concurrent.CountDownLatch
import kotlin.math.pow


/**
 *
 * @author Alexander Sosnovsky
 */
@Testcontainers
@WithMockk
internal class RabbitMqProducerIntegrationTest {

    @RegisterExtension
    val testDispatcherProvider = TestDispatcherProvider()

    @RelaxedMockK
    private lateinit var byteCounter: Counter

    @RelaxedMockK
    private lateinit var recordCounter: Counter

    private val factory = ConnectionFactory()

    @BeforeEach
    fun setUp() {
        factory.host = container.host
        factory.port = container.amqpPort
        factory.username = "the-user"
        factory.password = "the-password"
        factory.useNio()
    }

    private fun createExchangeAndQueue(
        channel: Channel, queueName: String, routingKey: String,
        type: String = "direct"
    ) {
        channel.exchangeDeclare(queueName, type, true)

        val queue = channel.queueDeclare(queueName, true, false, false, emptyMap()).queue
        channel.queueBind(queue, queueName, routingKey)
    }

    @Test
    @Timeout(50)
    internal fun `should produce the data to queue`(): Unit = testDispatcherProvider.run {

        val producerClient = RabbitMqProducer(
            concurrency = 1,
            connectionFactory = factory
        )

        val countDownLatch = CountDownLatch(1)

        producerClient.start()

        val connection = factory.newConnection()
        val channel = connection.createChannel()
        val receivedMessage = slot<Delivery>()
        createExchangeAndQueue(channel, "dest-2", "key-2")
        channel.basicConsume("dest-2", false,
            DeliverCallback { _, message ->
                receivedMessage.captured = message
                countDownLatch.countDown()
                channel.basicAck(message.envelope.deliveryTag, false)
            },
            CancelCallback { }
        )

        producerClient.execute(
            listOf(
                RabbitMqProducerRecord(
                    exchange = "dest-2",
                    routingKey = "key-2",
                    props = null,
                    value = "text-2".toByteArray()
                )
            )
        )

        // Wait for the message to be read.
        countDownLatch.await()

        producerClient.stop()
        connection.close(5000)

        assertThat(receivedMessage.captured).all {
            prop(Delivery::getBody).isEqualTo("text-2".toByteArray())
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
