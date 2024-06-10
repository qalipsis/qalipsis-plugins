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
            .withEnv("RABBITMQ_VM_MEMORY_HIGH_WATERMARK", "128MiB")

    }

}
