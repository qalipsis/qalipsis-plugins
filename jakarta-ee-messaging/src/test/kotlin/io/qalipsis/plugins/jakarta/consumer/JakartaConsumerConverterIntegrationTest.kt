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

import assertk.all
import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.index
import assertk.assertions.isEqualTo
import assertk.assertions.prop
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.Tags
import io.mockk.coEvery
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.verify
import io.mockk.verifyOrder
import io.qalipsis.api.context.StepOutput
import io.qalipsis.api.context.StepStartStopContext
import io.qalipsis.api.events.EventsLogger
import io.qalipsis.api.meters.CampaignMeterRegistry
import io.qalipsis.plugins.jakarta.Constants
import io.qalipsis.plugins.jakarta.JakartaDeserializer
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.CleanMockkRecordedCalls
import io.qalipsis.test.mockk.relaxedMockk
import jakarta.jms.Message
import jakarta.jms.TextMessage
import kotlinx.coroutines.channels.Channel
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory
import org.apache.activemq.artemis.jms.client.ActiveMQDestination
import org.apache.activemq.artemis.jms.client.ActiveMQSession
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.extension.RegisterExtension
import org.testcontainers.containers.GenericContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.pow


/**
 * @author Krawist Ngoben
 */
@CleanMockkRecordedCalls
@Testcontainers
internal class JakartaConsumerConverterIntegrationTest {

    @JvmField
    @RegisterExtension
    val testDispatcherProvider = TestDispatcherProvider()

    private val valueDeserializer: JakartaDeserializer<String> = relaxedMockk {
        every { deserialize(any()) } answers { firstArg<TextMessage>().text }
    }

    private val metersTags = relaxedMockk<Tags>()

    private val startStopContext = relaxedMockk<StepStartStopContext> {
        every { toMetersTags() } returns metersTags
    }

    private val consumedBytesCounter = relaxedMockk<Counter>()

    private val consumedRecordsCounter = relaxedMockk<Counter>()

    private val meterRegistry = relaxedMockk<CampaignMeterRegistry> {
        every { counter("jakarta-consume-value-bytes", refEq(metersTags)) } returns consumedBytesCounter
        every { counter("jakarta-consume-records", refEq(metersTags)) } returns consumedRecordsCounter
    }

    private val eventsLogger = relaxedMockk<EventsLogger>()

    private val tags: Map<String, String> = startStopContext.toEventTags()

    private lateinit var connectionFactory: ActiveMQConnectionFactory

    @BeforeAll
    fun initGlobal() {
        connectionFactory = ActiveMQConnectionFactory(
            "tcp://localhost:${container.getMappedPort(61616)}",
            Constants.CONTAINER_USER_NAME,
            Constants.CONTAINER_PASSWORD
        )
    }

    @Timeout(50)
    @Test
    internal fun `should deserialize without monitor`() = testDispatcherProvider.runTest {
        // when
        executeConversion()
        val consumedBytesCounter = relaxedMockk<Counter>()
        confirmVerified(consumedBytesCounter)
    }

    @Timeout(50)
    @Test
    internal fun `should deserialize and monitor`() = testDispatcherProvider.runTest {
        // when
        executeConversion(meterRegistry = meterRegistry, eventsLogger = eventsLogger)

        verify {
            consumedBytesCounter.increment(14.0)
            consumedRecordsCounter.increment()
            consumedBytesCounter.increment(14.0)
            consumedRecordsCounter.increment()
            consumedBytesCounter.increment(14.0)
            consumedRecordsCounter.increment()
        }

        verifyOrder {
            eventsLogger.info(
                "jakarta.consume.received.records",
                1,
                timestamp = any(),
                tags = tags
            )
            eventsLogger.info(
                "jakarta.consume.received.value-bytes",
                14,
                timestamp = any(),
                tags = tags
            )
            eventsLogger.info(
                "jakarta.consume.received.records",
                1,
                timestamp = any(),
                tags = tags
            )
            eventsLogger.info(
                "jakarta.consume.received.value-bytes",
                14,
                timestamp = any(),
                tags = tags
            )
            eventsLogger.info(
                "jakarta.consume.received.records",
                1,
                timestamp = any(),
                tags = tags
            )
            eventsLogger.info(
                "jakarta.consume.received.value-bytes",
                14,
                timestamp = any(),
                tags = tags
            )
        }
        confirmVerified(consumedBytesCounter, eventsLogger)
    }

    private suspend fun executeConversion(
        meterRegistry: CampaignMeterRegistry? = null,
        eventsLogger: EventsLogger? = null
    ) {

        val session = connectionFactory.createConnection().createSession() as ActiveMQSession

        val converter = JakartaConsumerConverter(valueDeserializer, meterRegistry, eventsLogger)

        val offset = AtomicLong(1)

        val destination1 = ActiveMQDestination.createDestination("dest-1", ActiveMQDestination.TYPE.QUEUE)
        val destination2 = ActiveMQDestination.createDestination("dest-2", ActiveMQDestination.TYPE.QUEUE)
        val destination3 = ActiveMQDestination.createDestination("dest-3", ActiveMQDestination.TYPE.QUEUE)

        val message1 =
            generateMessage(text = "test-message-1", destination = destination1, offset = 1, session = session)
        val message2 =
            generateMessage(text = "test-message-2", destination = destination2, offset = 2, session = session)
        val message3 =
            generateMessage(text = "test-message-3", destination = destination3, offset = 3, session = session)
        message3.jmsPriority = 9
        val channel = Channel<JakartaConsumerResult<String>>(3)
        val output = relaxedMockk<StepOutput<JakartaConsumerResult<String>>> {
            coEvery { send(any()) } coAnswers {
                channel.send(firstArg())
            }
        }

        //when
        converter.start(startStopContext)
        converter.supply(
            offset, message1, output
        )
        converter.supply(
            offset, message2, output
        )
        converter.supply(
            offset, message3, output
        )
        // Each message is sent in a unitary statement.
        val results = listOf(
            channel.receive(),
            channel.receive(),
            channel.receive()
        )

        // then
        assertThat(results).all {
            hasSize(3)
            index(0).all {
                prop(JakartaConsumerResult<*>::record).all {
                    prop(JakartaConsumerRecord<*>::timestamp).isEqualTo(0)
                    prop(JakartaConsumerRecord<*>::expiration).isEqualTo(0)
                    prop(JakartaConsumerRecord<*>::priority).isEqualTo(0)
                    prop(JakartaConsumerRecord<*>::correlationId).isEqualTo("correlation-id-1")
                    prop(JakartaConsumerRecord<*>::messageId).isEqualTo("ID:1")
                    prop(JakartaConsumerRecord<*>::offset).isEqualTo(1)
                    prop(JakartaConsumerRecord<*>::destination).isEqualTo(destination1)
                    prop(JakartaConsumerRecord<*>::value).isEqualTo("test-message-1")
                }
                prop(JakartaConsumerResult<*>::meters).all {
                    prop(JakartaConsumerMeters::consumedBytes).isEqualTo(14)
                }
            }

            index(1).all {
                prop(JakartaConsumerResult<*>::record).all {
                    prop(JakartaConsumerRecord<*>::timestamp).isEqualTo(0)
                    prop(JakartaConsumerRecord<*>::expiration).isEqualTo(0)
                    prop(JakartaConsumerRecord<*>::priority).isEqualTo(0)
                    prop(JakartaConsumerRecord<*>::correlationId).isEqualTo("correlation-id-2")
                    prop(JakartaConsumerRecord<*>::messageId).isEqualTo("ID:2")
                    prop(JakartaConsumerRecord<*>::offset).isEqualTo(2)
                    prop(JakartaConsumerRecord<*>::destination).isEqualTo(destination2)
                    prop(JakartaConsumerRecord<*>::value).isEqualTo("test-message-2")
                }
                prop(JakartaConsumerResult<*>::meters).all {
                    prop(JakartaConsumerMeters::consumedBytes).isEqualTo(14)
                }
            }

            index(2).all {
                prop(JakartaConsumerResult<*>::record).all {
                    prop(JakartaConsumerRecord<*>::timestamp).isEqualTo(0)
                    prop(JakartaConsumerRecord<*>::expiration).isEqualTo(0)
                    prop(JakartaConsumerRecord<*>::priority).isEqualTo(9)
                    prop(JakartaConsumerRecord<*>::correlationId).isEqualTo("correlation-id-3")
                    prop(JakartaConsumerRecord<*>::messageId).isEqualTo("ID:3")
                    prop(JakartaConsumerRecord<*>::offset).isEqualTo(3)
                    prop(JakartaConsumerRecord<*>::destination).isEqualTo(destination3)
                    prop(JakartaConsumerRecord<*>::value).isEqualTo("test-message-3")
                }
                prop(JakartaConsumerResult<*>::meters).all {
                    prop(JakartaConsumerMeters::consumedBytes).isEqualTo(14)
                }
            }
        }

    }

    private fun generateMessage(
        text: String,
        session: ActiveMQSession,
        offset: Long,
        destination: ActiveMQDestination
    ): Message {
        return session.createTextMessage(text).apply {
            jmsCorrelationID = "correlation-id-$offset"
            jmsMessageID = "ID:$offset"
            jmsDestination = destination
            jmsTimestamp = 0
            jmsExpiration = 0
            jmsPriority = 0
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
            withEnv(Constants.CONTAINER_USER_NAME_ENV_KEY, Constants.CONTAINER_USER_NAME)
            withEnv(Constants.CONTAINER_PASSWORD_ENV_KEY, Constants.CONTAINER_PASSWORD)
        }
    }
}
