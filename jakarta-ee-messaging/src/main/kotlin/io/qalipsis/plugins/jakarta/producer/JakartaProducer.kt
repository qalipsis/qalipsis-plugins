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

import io.qalipsis.api.context.StepName
import io.qalipsis.api.context.StepStartStopContext
import io.qalipsis.api.events.EventsLogger
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.meters.CampaignMeterRegistry
import io.qalipsis.api.meters.Counter
import io.qalipsis.api.report.ReportMessageSeverity
import io.qalipsis.api.sync.Slot
import io.qalipsis.api.sync.SuspendedCountLatch
import io.qalipsis.plugins.jakarta.destination.Destination
import io.qalipsis.plugins.jakarta.destination.Queue
import io.qalipsis.plugins.jakarta.destination.Topic
import io.qalipsis.plugins.jakarta.exception.BulkProducerException
import jakarta.jms.CompletionListener
import jakarta.jms.Connection
import jakarta.jms.Message
import jakarta.jms.MessageProducer
import jakarta.jms.Session
import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue
import kotlin.concurrent.thread

/**
 * Jakarta producer client to produce native Jakarta [jakarta.jms.Message]s to a Jakarta server.
 *
 * @property connectionFactory supplier for the JMS [jakarta.jms.Connection]
 * @property sessionFactory supplier for the JMS [jakarta.jms.Session]
 * @property meterRegistry the metrics for the produce operation
 * @property converter from a [JakartaProducerRecord] to a native JMS [jakarta.jms.Message]
 *
 * @author Krawist Ngoben
 */
internal open class JakartaProducer(
    private val stepName: StepName,
    private val connectionFactory: () -> Connection,
    private val sessionFactory: (connection: Connection) -> Session,
    private val converter: JakartaProducerConverter,
    private val producersCount: Int,
    private val eventsLogger: EventsLogger?,
    private val meterRegistry: CampaignMeterRegistry?
) {

    private lateinit var connection: Connection

    private var running = false

    private val eventPrefix = "jakarta.produce"

    private val meterPrefix = "jakarta-produce"

    private var recordsToProduce: Counter? = null

    private var producedBytesCounter: Counter? = null

    private var producedRecordsCounter: Counter? = null

    private var errorsCounter: Counter? = null

    private var threads = mutableListOf<Thread>()

    private lateinit var recordsToSend: BlockingQueue<SendingContext>

    /**
     * Prepares producer inside before execute.
     */
    fun start(context: StepStartStopContext) {
        val scenarioName = context.scenarioName
        val stepName = context.stepName
        meterRegistry?.apply {
            val metersTags = context.toMetersTags()
            recordsToProduce =
                counter(scenarioName, stepName, "$meterPrefix-producing-records", metersTags).report {
                    display(
                        format = "attempted rec: %,.0f",
                        severity = ReportMessageSeverity.INFO,
                        row = 0,
                        column = 0,
                        Counter::count
                    )
                }
            producedBytesCounter =
                counter(scenarioName, stepName, "$meterPrefix-produced-value-bytes", metersTags).report {
                    display(
                        format = "produced: %,.0f bytes",
                        severity = ReportMessageSeverity.INFO,
                        row = 0,
                        column = 3,
                        Counter::count
                    )
                }
            producedRecordsCounter =
                counter(scenarioName, stepName, "$meterPrefix-produced-records", metersTags).report {
                    display(
                        format = "produced rec: %,.0f",
                        severity = ReportMessageSeverity.INFO,
                        row = 0,
                        column = 2,
                        Counter::count
                    )
                }
            errorsCounter = counter(scenarioName, stepName, "$meterPrefix-produced-errors", metersTags)
        }
        connection = connectionFactory()
        connection.start()
        running = true

        recordsToSend = LinkedBlockingQueue()

        threads.forEach {
            kotlin.runCatching {
                it.interrupt()
            }
        }
        threads.clear()

        // Several threads are created to ensure the scalability for sending.
        threads += (1..producersCount).map { producerIndex ->
            createProducer(producerIndex)
        }
    }

    /**
     * According to the [documentation](https://jakarta.ee/specifications/messaging/3.0/jakarta-messaging-spec-3.0.html#classic-api-interfaces),
     * the [Session] should be kept and used in a single thread.
     *
     * Therefore, the producers threads are kept as single-threaded operations with an internal [Session].
     */
    private fun createProducer(producerIndex: Int) = thread(
        start = true,
        isDaemon = true,
        name = "${stepName}-jakarta-ee-producer-$producerIndex"
    ) {
        val session = sessionFactory(connection)
        val messageProducers = mutableMapOf<Destination, MessageProducer>()
        try {
            while (running) {
                recordsToSend.take()
                    .let { (pendingMessage, meters, contextEventTags, resultSlot, countDownLatch) ->
                        val messageProducer = messageProducers.computeIfAbsent(pendingMessage.destination) {
                            when (pendingMessage.destination) {
                                is Queue -> session.createProducer(session.createQueue(pendingMessage.destination.name))
                                is Topic -> session.createProducer(session.createTopic(pendingMessage.destination.name))
                                else -> throw IllegalArgumentException()
                            }
                        }
                        messageProducer.send(
                            converter.convert(pendingMessage, session),
                            buildListener(meters, pendingMessage, countDownLatch, contextEventTags, resultSlot)
                        )
                    }
            }
        } catch (e: InterruptedException) {
            // Do nothing.
        } finally {
            messageProducers.forEach { runCatching { it.value.close() } }
            messageProducers.clear()
            session.close()
        }
    }


    private fun buildListener(
        meters: JakartaProducerMeters,
        pendingMessage: JakartaProducerRecord,
        countDownLatch: SuspendedCountLatch,
        contextEventTags: Map<String, String>,
        resultSlot: Slot<Result<Unit>>
    ) = object : CompletionListener {

        override fun onCompletion(message: Message) {
            meters.producedRecords++
            meters.producedBytes += when (pendingMessage.messageType) {
                JakartaMessageType.TEXT -> "${pendingMessage.value}".toByteArray().size
                JakartaMessageType.BYTES -> (pendingMessage.value as? ByteArray)?.size ?: 0
                else -> 0
            }
            resultSlot.offer(Result.success(Unit))
            countDownLatch.blockingDecrement()
        }

        override fun onException(message: Message, exception: Exception) {
            eventsLogger?.error(
                "${eventPrefix}.produced.error",
                exception,
                tags = contextEventTags
            )
            errorsCounter?.increment()
            log.debug(exception) { exception.message }
            resultSlot.offer(Result.failure(exception))
            countDownLatch.blockingDecrement()
        }
    }


    /**
     * Executes producing [jakarta.jms.Message]s to Jakarta server.
     */
    suspend fun execute(
        messages: List<JakartaProducerRecord>,
        contextEventTags: Map<String, String>
    ): JakartaProducerMeters {
        val metersForCall = JakartaProducerMeters(messages.size)
        val countDownLatch = SuspendedCountLatch(messages.size.toLong())
        recordsToProduce?.increment(messages.size.toDouble())
        eventsLogger?.debug("${eventPrefix}.producing.records", messages.size, tags = contextEventTags)

        // Push the messages to the producers.
        val resultSlots = mutableListOf<Slot<Result<Unit>>>()
        recordsToSend.addAll(messages.map {
            SendingContext(
                it,
                metersForCall,
                contextEventTags,
                Slot<Result<Unit>>().also(resultSlots::add),
                countDownLatch
            )
        })

        // Wait for all the messages to be completed.
        countDownLatch.await()

        eventsLogger?.info("${eventPrefix}.produced.records", metersForCall.producedRecords, tags = contextEventTags)
        eventsLogger?.info("${eventPrefix}.produced.bytes", metersForCall.producedBytes, tags = contextEventTags)

        producedBytesCounter?.increment(metersForCall.producedBytes.toDouble())
        producedRecordsCounter?.increment(metersForCall.producedRecords.toDouble())

        val errors = resultSlots.mapNotNull { it.get().exceptionOrNull() }
        if (errors.isNotEmpty()) {
            throw BulkProducerException(errors)
        }
        return metersForCall
    }

    /**
     * Shutdown producer after execute.
     */
    fun stop() {
        //Stopping the thread.
        running = false
        threads.forEach {
            kotlin.runCatching {
                it.interrupt()
            }
        }
        threads.clear()
        meterRegistry?.apply {
            recordsToProduce = null
            producedBytesCounter = null
            producedRecordsCounter = null
            errorsCounter = null
        }
        running = false
        recordsToSend.clear()

        connection.stop()
        connection.close()
    }

    private data class SendingContext(
        val record: JakartaProducerRecord,
        val meters: JakartaProducerMeters,
        val contextEventTags: Map<String, String>,
        val result: Slot<Result<Unit>>,
        val countDown: SuspendedCountLatch
    )

    companion object {

        val log = logger()
    }

}
