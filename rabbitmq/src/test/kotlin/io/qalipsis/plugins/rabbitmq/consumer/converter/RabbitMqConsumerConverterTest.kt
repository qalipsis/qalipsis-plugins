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

package io.qalipsis.plugins.rabbitmq.consumer.converter

import assertk.all
import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.index
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import com.rabbitmq.client.AMQP
import com.rabbitmq.client.Delivery
import com.rabbitmq.client.Envelope
import io.mockk.coEvery
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.verify
import io.qalipsis.api.context.StepOutput
import io.qalipsis.api.messaging.deserializer.MessageDeserializer
import io.qalipsis.api.meters.Counter
import io.qalipsis.plugins.rabbitmq.consumer.RabbitMqConsumerRecord
import io.qalipsis.test.assertk.prop
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.relaxedMockk
import kotlinx.coroutines.channels.Channel
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.extension.RegisterExtension
import java.util.concurrent.atomic.AtomicLong

/**
 * @author Gabriel Moraes
 */
@WithMockk
internal class RabbitMqConsumerConverterTest {

    @RegisterExtension
    val testDispatcherProvider = TestDispatcherProvider()

    private val valueSerializer: MessageDeserializer<String> = relaxedMockk {
        every { deserialize(any()) } answers { firstArg<ByteArray>().decodeToString() }
    }

    private val counterBytes: Counter = relaxedMockk {}

    @Test
    @Timeout(2)
    fun `should deserialize without monitoring`() = testDispatcherProvider.runTest {
        val deliveryMessage1 = Delivery(
            Envelope(1L, false, "test", "test"),
            AMQP.BasicProperties(), "message".toByteArray()
        )
        val deliveryMessage2 = Delivery(
            Envelope(2L, false, "test", "test"),
            AMQP.BasicProperties(), "message2".toByteArray()
        )
        val deliveryMessage3 = Delivery(
            Envelope(3L, false, "test", "test"),
            AMQP.BasicProperties(), "message3".toByteArray()
        )

        val converter = RabbitMqConsumerConverter(
            valueSerializer
        )
        val channel = Channel<RabbitMqConsumerRecord<String>>(3)
        val output = relaxedMockk<StepOutput<RabbitMqConsumerRecord<String>>> {
            coEvery { send(any()) } coAnswers {
                channel.send(firstArg())
            }
        }

        // when
        converter.supply(AtomicLong(120), deliveryMessage1, output)
        converter.supply(AtomicLong(121), deliveryMessage2, output)
        converter.supply(AtomicLong(122), deliveryMessage3, output)


        // receives messages converted sent in the output channel.
        val results = listOf(channel.receive(), channel.receive(), channel.receive())

        // then
        assertThat(results).all {
            hasSize(3)
            index(0).all {
                prop("value").isNotNull().isEqualTo("message")
                prop("offset").isEqualTo(120L)
                prop("exchangeName").isEqualTo("test")
                prop("routingKey").isEqualTo("test")
                prop("properties").isNotNull()
                prop("consumedTimestamp").isNotNull()
            }
            index(1).all {
                prop("value").isNotNull().isEqualTo("message2")
                prop("offset").isEqualTo(121L)
                prop("exchangeName").isEqualTo("test")
                prop("routingKey").isEqualTo("test")
                prop("properties").isNotNull()
                prop("consumedTimestamp").isNotNull()
            }
            index(2).all {
                prop("value").isNotNull().isEqualTo("message3")
                prop("offset").isEqualTo(122L)
                prop("exchangeName").isEqualTo("test")
                prop("routingKey").isEqualTo("test")
                prop("properties").isNotNull()
                prop("consumedTimestamp").isNotNull()
            }
        }

        verify {
            valueSerializer.deserialize(eq(deliveryMessage1.body))
            valueSerializer.deserialize(eq(deliveryMessage2.body))
            valueSerializer.deserialize(eq(deliveryMessage3.body))
        }
        confirmVerified(counterBytes, valueSerializer)
    }

}
