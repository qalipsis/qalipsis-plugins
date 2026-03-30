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

package io.qalipsis.plugins.jakarta.producer

import assertk.all
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.prop
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.excludeRecords
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.verify
import io.qalipsis.api.context.StepStartStopContext
import io.qalipsis.api.events.EventsLogger
import io.qalipsis.api.meters.CampaignMeterRegistry
import io.qalipsis.api.meters.Counter
import io.qalipsis.api.meters.Meter
import io.qalipsis.plugins.jakarta.Constants
import io.qalipsis.plugins.jakarta.destination.Queue
import io.qalipsis.plugins.jakarta.destination.Topic
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.relaxedMockk
import jakarta.jms.BytesMessage
import jakarta.jms.Connection
import jakarta.jms.Session
import jakarta.jms.TextMessage
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory
import org.junit.jupiter.api.AfterEach
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

    @RelaxedMockK
    private lateinit var bytesCounter: Counter

    @RelaxedMockK
    private lateinit var producedRecordsCounter: Counter

    @RelaxedMockK
    private lateinit var recordsToProduceCounter: Counter

    @RelaxedMockK
    private lateinit var errorsCounter: Counter

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

    @AfterEach
    fun stop(){
        consumerSession.close()
        consumerConnection.close()
    }

    @Timeout(10)
    @Test
    internal fun `should produce all the data to queue`(): Unit = testDispatcherProvider.run {
        // given
        val tags: Map<String, String> = emptyMap()
        val eventsLogger = relaxedMockk<EventsLogger>()
        val context = relaxedMockk<StepStartStopContext> {
            every { toMetersTags() } returns tags
            every { scenarioName } returns "scenario-name"
            every { stepName } returns "step-name"
        }
        val meterRegistry = relaxedMockk<CampaignMeterRegistry> {
            every { counter("scenario-name", "step-name","jakarta-produce-producing-records", refEq(tags)) } returns recordsToProduceCounter
            every { counter("scenario-name", "step-name", "jakarta-produce-produced-value-bytes", refEq(tags)) } returns bytesCounter
            every { counter("scenario-name", "step-name","jakarta-produce-produced-records", refEq(tags)) } returns producedRecordsCounter
            every { counter("scenario-name", "step-name","jakarta-produce-produced-errors", refEq(tags)) } returns errorsCounter
            every { recordsToProduceCounter.report(any()) } returns recordsToProduceCounter
            every { bytesCounter.report(any()) } returns bytesCounter
            every { producedRecordsCounter.report(any()) } returns producedRecordsCounter
        }

        val connection = connectionFactory.createConnection()

        val produceClient = JakartaProducer(
            stepName = "step1",
            connectionFactory = { connection },
            sessionFactory =  { connection.createSession()},
            converter = JakartaProducerConverter(),
            producersCount = 1,
            eventsLogger,
            meterRegistry,
        )

        produceClient.start(context)

        excludeRecords {
            eventsLogger.toString()
        }

        // when
        val result = produceClient.execute(
            listOf(
                JakartaProducerRecord(
                    destination = Queue("queue-1"),
                    messageType = JakartaMessageType.TEXT,
                    value = "hello-queue"
                ),
                JakartaProducerRecord(
                    destination = Queue("queue-1"),
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
        confirmVerified(bytesCounter, recordsToProduceCounter, producedRecordsCounter, errorsCounter, eventsLogger)

        // when
        val queueConsumer = consumerSession.createConsumer(consumerSession.createQueue("queue-1"))
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

    @Timeout(10)
    @Test
    internal fun `should produce all the data to topic`(): Unit = testDispatcherProvider.run {
        // given
        val tags: Map<String, String> = emptyMap()
        val topicConsumer = consumerSession.createConsumer(consumerSession.createTopic("topic-1"))
        val eventsLogger = relaxedMockk<EventsLogger>()
        val startStopContext = relaxedMockk<StepStartStopContext> {
            every { toMetersTags() } returns emptyMap()
            every { scenarioName } returns "scenario-name"
            every { stepName } returns "step-name"
        }
        val meterRegistry = relaxedMockk<CampaignMeterRegistry> {
            every { counter("scenario-name", "step-name","jakarta-produce-producing-records", refEq(tags)) } returns recordsToProduceCounter
            every { counter("scenario-name", "step-name","jakarta-produce-produced-value-bytes", refEq(tags)) } returns bytesCounter
            every { counter("scenario-name", "step-name","jakarta-produce-produced-records", refEq(tags)) } returns producedRecordsCounter
            every { counter("scenario-name", "step-name","jakarta-produce-produced-errors", refEq(tags)) } returns errorsCounter
            every { recordsToProduceCounter.report(any()) } returns recordsToProduceCounter
            every { bytesCounter.report(any()) } returns bytesCounter
            every { producedRecordsCounter.report(any()) } returns producedRecordsCounter
        }

        val connection = connectionFactory.createConnection()

        val produceClient = JakartaProducer(
            stepName = "step1",
            connectionFactory = { connection },
            sessionFactory =  { connection.createSession()},
            converter = JakartaProducerConverter(),
            producersCount = 1,
            eventsLogger,
            meterRegistry
        )

        produceClient.start(startStopContext)

        excludeRecords {
            eventsLogger.toString()
        }

        // when
        val result = produceClient.execute(
            listOf(
                JakartaProducerRecord(
                    destination = Topic("topic-1"),
                    messageType = JakartaMessageType.TEXT,
                    value = "hello-topic"
                ),
                JakartaProducerRecord(
                    destination = Topic("topic-1"),
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
        confirmVerified(bytesCounter, recordsToProduceCounter, producedRecordsCounter, errorsCounter, eventsLogger)

        // when
        val message1 = topicConsumer.receive()
        val message2 = topicConsumer.receive()
        topicConsumer.close()

        // then
        assertThat(message1).isInstanceOf(TextMessage::class).all {
            transform("text") { it.text }.isEqualTo("hello-topic")
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

        @JvmField
        @RegisterExtension
        val testDispatcherProvider = TestDispatcherProvider()

    }
}
