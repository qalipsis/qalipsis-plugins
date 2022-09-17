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

package io.qalipsis.plugins.rabbitmq.consumer

import com.rabbitmq.client.CancelCallback
import com.rabbitmq.client.Connection
import com.rabbitmq.client.ConnectionFactory
import com.rabbitmq.client.DeliverCallback
import com.rabbitmq.client.Delivery
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tags
import io.qalipsis.api.context.StepStartStopContext
import io.qalipsis.api.events.EventsLogger
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
    private val connectionFactory: ConnectionFactory,
    private val meterRegistry: MeterRegistry?,
    private val eventsLogger: EventsLogger?
) : DatasourceIterativeReader<Delivery> {

    private lateinit var executorService: ExecutorService

    private var resultChannel: Channel<Delivery>? = null

    private var running = false

    private val channels: MutableList<com.rabbitmq.client.Channel> = mutableListOf()

    private lateinit var connection: Connection

    private val eventPrefix = "rabbitmq.consume"

    private val meterPrefix: String = "rabbitmq-consume"

    private var meterBytesCounter: Counter? = null

    private var meterRecordsCounter: Counter? = null

    private var meterTags: Tags? = null

    private var eventTags: Map<String, String>? = null

    override fun start(context: StepStartStopContext) {

        meterTags = context.toMetersTags()
        eventTags = context.toEventTags()

        initMonitoringMetrics()

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

    private fun initMonitoringMetrics() {
        meterRegistry?.apply {
            meterBytesCounter = meterRegistry.counter("${meterPrefix}-bytes", meterTags)
            meterRecordsCounter = meterRegistry.counter("${meterPrefix}-records", meterTags)
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
                    updateMonitoringStats(message.body)
                    resultChannel?.trySend(message)
                    channel.basicAck(message.envelope.deliveryTag, false)
                },
                CancelCallback { }
            )
        } catch (e: Exception) {
            log.error(e) { "An error occurred in the rabbitMQ consumer: ${e.message}" }
        }
    }

    private fun updateMonitoringStats(message: ByteArray) {
        meterBytesCounter?.increment(message.size.toDouble())
        meterRecordsCounter?.increment()
        eventsLogger?.apply {
            info("${eventPrefix}.bytes", message.size, tags = eventTags!!)
            info("${eventPrefix}.records", 1, tags = eventTags!!)
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

        stopMonitoringMetrics()

        log.debug { "RabbitMQ consumer was stopped" }
    }

    private fun stopMonitoringMetrics() {
        meterRegistry?.apply {
            remove(meterBytesCounter)
            remove(meterRecordsCounter)
            meterBytesCounter = null
            meterRecordsCounter = null
        }
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
