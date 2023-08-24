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

package io.qalipsis.plugins.jakarta.producer

import assertk.all
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.prop
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.verify
import io.qalipsis.api.context.StepStartStopContext
import io.qalipsis.api.events.EventsLogger
import io.qalipsis.api.meters.CampaignMeterRegistry
import io.qalipsis.api.meters.Counter
import io.qalipsis.api.meters.Meter
import io.qalipsis.plugins.jakarta.Constants
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.relaxedMockk
import jakarta.jms.BytesMessage
import jakarta.jms.Connection
import jakarta.jms.MessageConsumer
import jakarta.jms.Session
import jakarta.jms.TextMessage
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory
import org.apache.activemq.artemis.jms.client.ActiveMQDestination
import org.apache.activemq.artemis.jms.client.ActiveMQQueue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.extension.RegisterExtension
import org.testcontainers.containers.GenericContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import kotlin.math.pow

/**
 * @author Krawist Ngoben
 */
@Testcontainers
@WithMockk
internal class JakartaProducerIntegrationTest {

    @JvmField
    @RegisterExtension
    val testDispatcherProvider = TestDispatcherProvider()

    @RelaxedMockK
    private lateinit var bytesCounter: Counter

    @RelaxedMockK
    private lateinit var producedRecordsCounter: Counter

    @RelaxedMockK
    private lateinit var recordsToProduceCounter: Counter

    private lateinit var connectionFactory: ActiveMQConnectionFactory

    private lateinit var consumerConnection: Connection

    private lateinit var consumerSession: Session


    @BeforeEach
    fun initGlobal() {
        connectionFactory = ActiveMQConnectionFactory(
            "tcp://localhost:${container.getMappedPort(61616)}",
            Constants.CONTAINER_USER_NAME,
            Constants.CONTAINER_PASSWORD
        )
    }

    @BeforeEach
    fun setUp() {
        consumerConnection = connectionFactory.createConnection()
        consumerConnection.start()

        consumerSession = consumerConnection.createSession(false, Session.AUTO_ACKNOWLEDGE)
    }

    private fun prepareQueueConsumer(queueName: String): MessageConsumer {
        return consumerSession.createConsumer(consumerSession.createQueue(queueName))
    }

    @Timeout(10)
    @Test
    internal fun `should produce all the data to queue`(): Unit = testDispatcherProvider.run {
        // given
        val tags: Map<String, String> = emptyMap()
        val eventsLogger = relaxedMockk<EventsLogger>()
        val context = relaxedMockk<StepStartStopContext> {
            every { toEventTags() } returns tags
            every { scenarioName } returns "scenario-name"
            every { stepName } returns "step-name"
        }
        val meterRegistry = relaxedMockk<CampaignMeterRegistry> {
            every { counter("scenario-name", "step-name","jakarta-produce-producing-records", refEq(tags)) } returns recordsToProduceCounter
            every { counter("scenario-name", "step-name", "jakarta-produce-produced-value-bytes", refEq(tags)) } returns bytesCounter
            every { counter("scenario-name", "step-name","jakarta-produce-produced-records", refEq(tags)) } returns producedRecordsCounter
            every { recordsToProduceCounter.report(any()) } returns recordsToProduceCounter
            every { bytesCounter.report(any()) } returns bytesCounter
            every { producedRecordsCounter.report(any()) } returns producedRecordsCounter
        }
        val produceClient = JakartaProducer(
            connectionFactory = { connectionFactory.createConnection() },
            converter = JakartaProducerConverter(),
            eventsLogger,
            meterRegistry
        )

        produceClient.start(context)

        // when
        val result = produceClient.execute(
            listOf(
                JakartaProducerRecord(
                    destination = ActiveMQQueue.createDestination("queue-1", ActiveMQDestination.TYPE.QUEUE),
                    messageType = JakartaMessageType.TEXT,
                    value = "hello-queue"
                ),
                JakartaProducerRecord(
                    destination = ActiveMQQueue.createDestination("queue-1", ActiveMQDestination.TYPE.QUEUE),
                    messageType = JakartaMessageType.BYTES,
                    value = "another message".toByteArray()
                )
            ),
            tags
        )
        produceClient.stop()

        // then
        assertThat(result).all {
            prop(JakartaProducerMeters::recordsToProduce).isEqualTo(2)
            prop(JakartaProducerMeters::producedRecords).isEqualTo(2)
            prop(JakartaProducerMeters::producedBytes).isEqualTo(26)
        }
        verify {
            recordsToProduceCounter.increment(2.0)
            producedRecordsCounter.increment(2.0)
            bytesCounter.increment(26.0)
            bytesCounter.report(any<Meter.ReportingConfiguration<Counter>.() -> Unit>())
            recordsToProduceCounter.report(any<Meter.ReportingConfiguration<Counter>.() -> Unit>())
            producedRecordsCounter.report(any<Meter.ReportingConfiguration<Counter>.() -> Unit>())
            eventsLogger.debug("jakarta.produce.producing.records", 2, any(), tags = refEq(tags))
            eventsLogger.info("jakarta.produce.produced.records", 2, any(), tags = refEq(tags))
            eventsLogger.info("jakarta.produce.produced.bytes", 26, any(), tags = refEq(tags))
        }
        confirmVerified(bytesCounter, recordsToProduceCounter, producedRecordsCounter, eventsLogger)

        // when
        val queueConsumer = prepareQueueConsumer("queue-1")
        val message1 = queueConsumer.receive()
        val message2 = queueConsumer.receive()
        queueConsumer.close()

        // then
        assertThat(message1).isInstanceOf(TextMessage::class).all {
            transform("text") { it.text }.isEqualTo("hello-queue")
        }
        assertThat(message2).isInstanceOf(BytesMessage::class).all {
            transform("body") {
                val byteArray = ByteArray(it.bodyLength.toInt())
                it.reset()
                it.readBytes(byteArray)
                byteArray.toString(Charsets.UTF_8)
            }.isEqualTo("another message")
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