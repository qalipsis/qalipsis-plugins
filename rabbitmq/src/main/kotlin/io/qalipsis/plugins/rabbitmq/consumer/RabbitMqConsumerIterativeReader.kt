package io.qalipsis.plugins.rabbitmq.consumer

import com.rabbitmq.client.CancelCallback
import com.rabbitmq.client.Connection
import com.rabbitmq.client.ConnectionFactory
import com.rabbitmq.client.DeliverCallback
import com.rabbitmq.client.Delivery
import io.qalipsis.api.context.StepStartStopContext
import io.qalipsis.api.lang.tryAndLog
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.steps.datasource.DatasourceIterativeReader
import kotlinx.coroutines.channels.Channel
import java.time.Duration
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.validation.constraints.Positive

/**
 * Implementation of [DatasourceIterativeReader] to consumer records from RabbitMQ queues.
 *
 * This implementation supports multiple consumers using the property [concurrency].
 *
 * @property concurrency quantity of concurrent consumers.
 * @property prefetchCount configuration for RabbitMQ qos, see more [here](https://www.rabbitmq.com/consumer-prefetch.html).
 * @property queue name of the queue.
 * @property connectionFactory supplier to create the connection with the RabbitMQ broker.
 *
 * @author Gabriel Moraes
 */
internal class RabbitMqConsumerIterativeReader(
    @Positive private val concurrency: Int,
    private val prefetchCount: Int,
    private val queue: String,
    private val connectionFactory: ConnectionFactory
) : DatasourceIterativeReader<Delivery> {

    private lateinit var executorService: ExecutorService

    private var resultChannel: Channel<Delivery>? = null

    private var running = false

    private val channels: MutableList<com.rabbitmq.client.Channel> = mutableListOf()

    private lateinit var connection: Connection

    override fun start(context: StepStartStopContext) {
        running = true

        resultChannel = Channel(Channel.UNLIMITED)
        executorService = Executors.newFixedThreadPool(concurrency)
        connection = connectionFactory.newConnection(executorService)

        repeat(concurrency) {
            try {
                startConsumer(connection)
            } catch (e: Exception) {
                log.error(e) { "An error occurred while starting the rabbitMQ consumer: ${e.message}" }
                throw e
            }
        }
    }

    private fun startConsumer(connection: Connection) {
        val channel = connection.createChannel()
        channels.add(channel)

        try {
            channel.basicQos(prefetchCount)
            channel.basicConsume(queue, false,
                DeliverCallback { consumerTag, message ->
                    log.trace { "Message received for consumer tag: $consumerTag" }
                    resultChannel?.trySend(message)?.getOrThrow()
                    channel.basicAck(message.envelope.deliveryTag, false)
                },
                CancelCallback { }
            )
        } catch (e: Exception) {
            log.error(e) { "An error occurred in the rabbitMQ consumer: ${e.message}" }
        }
    }

    override fun stop(context: StepStartStopContext) {
        log.trace { "Stopping the RabbitMQ consumer" }
        running = false

        channels.forEach { tryAndLog(log) { it.close() } }
        channels.clear()

        tryAndLog(log) { connection.close(CLOSE_TIMEOUT.toMillis().toInt()) }

        executorService.shutdown()
        executorService.awaitTermination(2 * CLOSE_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)

        resultChannel = null
        log.debug { "RabbitMQ consumer was stopped" }
    }

    override suspend fun hasNext(): Boolean {
        return running
    }

    override suspend fun next(): Delivery {
        return resultChannel!!.receive()
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
