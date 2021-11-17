package io.qalipsis.plugins.rabbitmq.consumer

import assertk.all
import assertk.assertThat
import assertk.assertions.containsAll
import assertk.assertions.hasSize
import assertk.assertions.index
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotEqualTo
import assertk.assertions.prop
import com.rabbitmq.client.Channel
import com.rabbitmq.client.Connection
import com.rabbitmq.client.ConnectionFactory
import com.rabbitmq.client.Delivery
import com.rabbitmq.client.Envelope
import com.rabbitmq.client.MessageProperties
import io.aerisconsulting.catadioptre.getProperty
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tag
import io.mockk.impl.annotations.RelaxedMockK
import io.qalipsis.api.events.EventsLogger
import io.qalipsis.plugins.rabbitmq.Constants.DOCKER_IMAGE
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.relaxedMockk
import io.qalipsis.test.mockk.verifyExactly
import io.qalipsis.test.mockk.verifyNever
import io.qalipsis.test.mockk.verifyOnce
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.assertThrows
import org.testcontainers.containers.RabbitMQContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import java.time.Duration
import kotlin.math.pow

/**
 * @author Gabriel Moraes
 */
@Testcontainers
@WithMockk
internal class RabbitMqConsumerIterativeReaderIntegrationTest {

    private lateinit var connection: Connection

    private var initialized = false

    private lateinit var reader: RabbitMqConsumerIterativeReader

    private val factory = ConnectionFactory()

    @RelaxedMockK
    private lateinit var meterRegistry: MeterRegistry

    @RelaxedMockK
    private lateinit var eventsLogger: EventsLogger

    @BeforeEach
    internal fun setUp() {

        factory.host = container.host
        factory.port = container.amqpPort
        factory.useNio()

        if (!initialized) {
            connection = factory.newConnection()

            initialized = true
        }

    }

    @AfterAll
    internal fun tearDown() {
        connection.close(Duration.ofSeconds(30).toMillis().toInt())
        initialized = false
    }

    private fun createExchangeAndQueue(channel: Channel, queueName: String, type: String = "direct") {
        channel.exchangeDeclare(queueName, type, true)

        val queue = channel.queueDeclare(queueName, true, false, false, emptyMap()).queue
        channel.queueBind(queue, queueName, queueName)
    }

    private fun publishRecords(channel: Channel, queueName: String, times: Int = 100): MutableList<String> {
        return publishRecords(channel, queueName, queueName, times)
    }

    private fun publishRecords(channel: Channel, exchangeName: String, routingKey: String, times: Int = 100):
            MutableList<String> {
        val records = mutableListOf<String>()
        for (i in 1..times) {
            records.add("A$i")
            channel.basicPublish(
                exchangeName, routingKey,
                MessageProperties.PERSISTENT_TEXT_PLAIN, "A$i".toByteArray()
            )
        }

        channel.close()
        return records
    }

    @Test
    @Timeout(10)
    internal fun `should always have next at start but not at stop`() = runBlocking {
        reader = RabbitMqConsumerIterativeReader(
            2,
            20,
            "test",
            factory,
            meterRegistry,
            eventsLogger
        )

        reader.start(relaxedMockk())
        Assertions.assertTrue(reader.hasNext())

        reader.stop(relaxedMockk())
        Assertions.assertFalse(reader.hasNext())
    }

    @Test
    @Timeout(10)
    internal fun `should accept start after stop and consume`() = runBlocking {

        val queueName = "test-start-stop"
        val channel = connection.createChannel()
        createExchangeAndQueue(channel, queueName)

        reader = RabbitMqConsumerIterativeReader(
            2,
            20,
            queueName,
            factory,
            meterRegistry,
            eventsLogger
        )
        reader.start(relaxedMockk())
        val initialChannel = reader.getProperty<kotlinx.coroutines.channels.Channel<*>>("resultChannel")

        reader.stop(relaxedMockk())

        val recordsPublished = publishRecords(channel, queueName, 10)

        reader.start(relaxedMockk())
        val afterStopStartChannel = reader.getProperty<kotlinx.coroutines.channels.Channel<*>>("resultChannel")
        val received = mutableListOf<Delivery>()

        while (received.size < 10) {
            val record = reader.next()
            received.add(record)
        }

        reader.stop(relaxedMockk())

        assertThat(afterStopStartChannel).isInstanceOf(kotlinx.coroutines.channels.Channel::class)
            .isNotEqualTo(initialChannel)
        assertThat(received).all {
            hasSize(10)
            index(0).all {
                prop("envelope") { Delivery::getEnvelope.call(it) }.all {
                    prop("exchange") { Envelope::getExchange.call(it) }.isEqualTo("test-start-stop")
                }
            }
            transform { delivery -> delivery.map { it.body.decodeToString() } }.all {
                containsAll(*recordsPublished.toTypedArray())
            }
        }
    }

    @Test
    @Timeout(10)
    internal fun `should work without monitoring`() = runBlocking {
        val queueName = "test"

        reader = RabbitMqConsumerIterativeReader(
            2,
            20,
            queueName,
            factory,
            null,
            null
        )

        val channel = connection.createChannel()

        createExchangeAndQueue(channel, queueName)
        val recordsPublished = publishRecords(channel, queueName)

        reader.start(relaxedMockk())

        verifyNever {
            meterRegistry.counter("rabbitmq-consume-bytes", any<Iterable<Tag>>())
            meterRegistry.counter("rabbitmq-consume-records", any<Iterable<Tag>>())
        }

        verifyNever {
            eventsLogger.info("rabbitmq.consume.records", any<Int>(), any(), tags = any<Map<String, String>>())
            eventsLogger.info("rabbitmq.consume.bytes", any<Int>(), any(), tags = any<Map<String, String>>())
        }

        // when
        val received = mutableListOf<Delivery>()
        while (received.size < 100) {
            val records = reader.next()
            received.add(records)
        }

        assertThat(received).all {
            hasSize(100)
            index(0).all {
                prop("envelope") { Delivery::getEnvelope.call(it) }.all {
                    prop("exchange") { Envelope::getExchange.call(it) }.isEqualTo("test")
                }
            }
            transform { delivery -> delivery.map { it.body.decodeToString() } }.all {
                containsAll(*recordsPublished.toTypedArray())
            }
        }

        // No other message will be read.
        assertThrows<TimeoutCancellationException> {
            withTimeout(1000) {
                reader.next()
            }
        }

        reader.stop(relaxedMockk())

        verifyNever {
            meterRegistry.remove(any<Counter>())
        }
    }

    @Test
    @Timeout(10)
    internal fun `should consume all the data from queue in a direct exchange type`() = runBlocking {
        val queueName = "test"

        reader = RabbitMqConsumerIterativeReader(
            2,
            20,
            queueName,
            factory,
            meterRegistry,
            eventsLogger
        )

        val channel = connection.createChannel()

        createExchangeAndQueue(channel, queueName)
        val recordsPublished = publishRecords(channel, queueName)

        reader.start(relaxedMockk())

        verifyOnce {
            meterRegistry.counter("rabbitmq-consume-bytes", any<Iterable<Tag>>())
            meterRegistry.counter("rabbitmq-consume-records", any<Iterable<Tag>>())
        }
        // when
        val received = mutableListOf<Delivery>()
        while (received.size < 100) {
            val records = reader.next()
            received.add(records)
        }

        assertThat(received).all {
            hasSize(100)
            index(0).all {
                prop("envelope") { Delivery::getEnvelope.call(it) }.all {
                    prop("exchange") { Envelope::getExchange.call(it) }.isEqualTo("test")
                }
            }
            transform { delivery -> delivery.map { it.body.decodeToString() } }.all {
                containsAll(*recordsPublished.toTypedArray())
            }
        }

        // No other message will be read.
        assertThrows<TimeoutCancellationException> {
            withTimeout(1000) {
                reader.next()
            }
        }

        reader.stop(relaxedMockk())

        verifyExactly(2) {
            meterRegistry.remove(any<Counter>())
        }
    }

    @Test
    @Timeout(10)
    internal fun `should consume all the data from queue in a fanout exchange type`() = runBlocking {
        val exchangeName = "test-fanout"
        val queueName = "test-fanout-queue"
        reader = RabbitMqConsumerIterativeReader(
            2,
            20,
            queueName,
            factory,
            meterRegistry,
            eventsLogger
        )

        val channel = connection.createChannel()

        createExchangeAndQueue(channel, exchangeName, "fanout")

        val queue = channel.queueDeclare(queueName, true, false, false, emptyMap()).queue
        channel.queueBind(queue, exchangeName, "")

        val recordsPublished = publishRecords(channel, exchangeName, "", 50)

        reader.start(relaxedMockk())

        // when
        val received = mutableListOf<Delivery>()
        while (received.size < 50) {
            val records = reader.next()
            received.add(records)
        }

        assertThat(received).all {
            hasSize(50)
            index(0).all {
                prop("envelope") { Delivery::getEnvelope.call(it) }.all {
                    prop("exchange") { Envelope::getExchange.call(it) }.isEqualTo("test-fanout")
                    prop("routing-key") { Envelope::getRoutingKey.call(it) }.isEqualTo("")
                }
            }
            transform { delivery -> delivery.map { it.body.decodeToString() } }.all {
                containsAll(*recordsPublished.toTypedArray())
            }
        }

        // No other message will be read.
        assertThrows<TimeoutCancellationException> {
            withTimeout(1000) {
                reader.next()
            }
        }

        reader.stop(relaxedMockk())
    }

    @Test
    @Timeout(10)
    internal fun `should consume all the data from queue in a topic exchange type`() = runBlocking {
        val exchangeName = "test-topic"
        val queueName = "test-topic-queue"
        reader = RabbitMqConsumerIterativeReader(
            2,
            20,
            queueName,
            factory,
            meterRegistry,
            eventsLogger
        )

        val channel = connection.createChannel()

        createExchangeAndQueue(channel, exchangeName, "topic")

        val queue = channel.queueDeclare(queueName, true, false, false, emptyMap()).queue
        channel.queueBind(queue, exchangeName, "*.topic.*")

        val recordsPublished = publishRecords(channel, exchangeName, "publish.topic.test", 20)

        reader.start(relaxedMockk())

        // when
        val received = mutableListOf<Delivery>()
        while (received.size < 20) {
            val records = reader.next()
            received.add(records)
        }

        assertThat(received).all {
            hasSize(20)
            index(0).all {
                prop("envelope") { Delivery::getEnvelope.call(it) }.all {
                    prop("exchange") { Envelope::getExchange.call(it) }.isEqualTo("test-topic")
                    prop("routing-key") { Envelope::getRoutingKey.call(it) }.isEqualTo("publish.topic.test")
                }
            }
            transform { delivery -> delivery.map { it.body.decodeToString() } }.all {
                containsAll(*recordsPublished.toTypedArray())
            }
        }

        // No other message will be read.
        assertThrows<TimeoutCancellationException> {
            withTimeout(1000) {
                reader.next()
            }
        }

        reader.stop(relaxedMockk())
    }

    companion object {
        @Container
        @JvmStatic
        private val container = RabbitMQContainer(DockerImageName.parse(DOCKER_IMAGE))
            .withCreateContainerCmdModifier { it.hostConfig!!.withMemory(256 * 1024.0.pow(2).toLong()).withCpuCount(2) }
            .withEnv("RABBITMQ_VM_MEMORY_HIGH_WATERMARK", "128MiB")
    }
}