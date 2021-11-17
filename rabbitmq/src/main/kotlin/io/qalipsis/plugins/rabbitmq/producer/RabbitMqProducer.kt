package io.qalipsis.plugins.rabbitmq.producer

import com.rabbitmq.client.Channel
import com.rabbitmq.client.Connection
import com.rabbitmq.client.ConnectionFactory
import io.qalipsis.api.lang.tryAndLogOrNull
import io.qalipsis.api.logging.LoggerHelper.logger
import java.time.Duration
import java.util.concurrent.BlockingQueue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import javax.validation.constraints.Positive

/**
 * RabbitMQ producer client to produce messages to the RabbitMQ broker.
 *
 * This implementation supports multiple producers using the property [concurrency]
 *
 * @property concurrency quantity of concurrent producers.
 * @property connectionFactory supplier to create the connection with the RabbitMQ broker.
 * @property monitoring the metrics for the produce operation
 *
 * @author Alexander Sosnovsky
 */
internal class RabbitMqProducer(
    @Positive private val concurrency: Int,
    private val connectionFactory: ConnectionFactory
) {

    private lateinit var executorService: ExecutorService

    private val channels = ConcurrentHashMap<String, Channel>()

    private lateinit var connection: Connection

    private var running = false

    private lateinit var publicationThread: Thread

    private lateinit var messageQueue: BlockingQueue<RabbitMqProducerRecord>

    /**
     * Prepares producers inside before execute.
     */
    fun start() {
        executorService = Executors.newFixedThreadPool(concurrency)
        connection = connectionFactory.newConnection(executorService)
        messageQueue = LinkedBlockingQueue()

        channels.clear()
        running = true

        publicationThread = Thread({
            while (running) {
                try {
                    val message = messageQueue.take()
                    channels.computeIfAbsent(message.exchange) {
                        connection.createChannel()
                    }.run {
                        basicPublish(message.exchange, message.routingKey, message.props, message.value)
                    }
                } catch (e: InterruptedException) {
                    // Nothing to do here.
                } catch (e: Throwable) {
                    log.error(e) { e.message }
                }
            }
        }, "rabbitmq-publisher-${System.currentTimeMillis()}")
        publicationThread.start()
    }

    /**
     * Publishes records to the RabbitMQ platform.
     */
    fun execute(messages: List<RabbitMqProducerRecord>) {
        messageQueue.addAll(messages)
    }

    /**
     * Shutdown producers after execute.
     */
    fun stop() {
        running = false
        tryAndLogOrNull(log) { publicationThread.interrupt() }
        channels.forEach { tryAndLogOrNull(log) { it.value.close() } }
        channels.clear()

        tryAndLogOrNull(log) { connection.close(CLOSE_TIMEOUT.toMillis().toInt()) }

        executorService.shutdown()
        executorService.awaitTermination(2 * CLOSE_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)
    }

    companion object {

        /**
         * Timeout used to close the connection with RabbitMQ broker.
         */
        private val CLOSE_TIMEOUT = Duration.ofSeconds(10)

        @JvmStatic
        private val log = logger()
    }

}
