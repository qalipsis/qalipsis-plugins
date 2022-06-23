package io.qalipsis.plugins.jms.producer

import assertk.all
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.prop
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tags
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.verify
import io.qalipsis.api.events.EventsLogger
import io.qalipsis.plugins.jms.Constants
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.relaxedMockk
import org.apache.activemq.ActiveMQConnectionFactory
import org.apache.activemq.command.ActiveMQQueue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.testcontainers.containers.GenericContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import javax.jms.BytesMessage
import javax.jms.Connection
import javax.jms.MessageConsumer
import javax.jms.Session
import javax.jms.TextMessage
import kotlin.math.pow


/**
 *
 * @author Alexander Sosnovsky
 */
@Testcontainers
@WithMockk
internal class JmsProducerIntegrationTest {

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

    private lateinit var producerConnection: Connection

    private lateinit var producerSession: Session


    @BeforeEach
    fun initGlobal() {
        connectionFactory = ActiveMQConnectionFactory("tcp://localhost:" + container.getMappedPort(61616))
    }

    @BeforeEach
    fun setUp() {
        producerConnection = connectionFactory.createConnection()
        producerConnection.start()

        producerSession = producerConnection.createSession(false, Session.AUTO_ACKNOWLEDGE)
    }

    private fun prepareQueueConsumer(queueName: String): MessageConsumer {
        return producerSession.createConsumer(producerSession.createQueue(queueName))
    }

    @Test
    internal fun `should produce all the data to queue`(): Unit = testDispatcherProvider.run {
        // given
        val tags: Map<String, String> = emptyMap()
        val eventsLogger = relaxedMockk<EventsLogger>()
        val metersTags = relaxedMockk<Tags>()
        val meterRegistry = relaxedMockk<MeterRegistry> {
            every { counter("jms-produce-producing-records", refEq(metersTags)) } returns recordsToProduceCounter
            every { counter("jms-produce-produced-value-bytes", refEq(metersTags)) } returns bytesCounter
            every { counter("jms-produce-produced-records", refEq(metersTags)) } returns producedRecordsCounter
        }
        val produceClient = JmsProducer(
            connectionFactory = { connectionFactory.createConnection() },
            converter = JmsProducerConverter(),
            eventsLogger,
            meterRegistry
        )

        produceClient.start(metersTags)

        // when
        val result = produceClient.execute(
            listOf(
                JmsProducerRecord(
                    destination = ActiveMQQueue().createDestination("queue-1"),
                    messageType = JmsMessageType.TEXT,
                    value = "hello-queue"
                ),
                JmsProducerRecord(
                    destination = ActiveMQQueue().createDestination("queue-1"),
                    messageType = JmsMessageType.BYTES,
                    value = "another message".toByteArray()
                )
            ),
            tags
        )
        produceClient.stop()

        // then
        assertThat(result).all {
            prop(JmsProducerMeters::recordsToProduce).isEqualTo(2)
            prop(JmsProducerMeters::producedRecords).isEqualTo(2)
            prop(JmsProducerMeters::producedBytes).isEqualTo(26)
        }
        verify {
            recordsToProduceCounter.increment(2.0)
            producedRecordsCounter.increment(2.0)
            bytesCounter.increment(26.0)

            eventsLogger.debug("jms.produce.producing.records", 2, any(), tags = refEq(tags))
            eventsLogger.info("jms.produce.produced.records", 2, any(), tags = refEq(tags))
            eventsLogger.info("jms.produce.produced.bytes", 26, any(), tags = refEq(tags))
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
        }

    }

}
