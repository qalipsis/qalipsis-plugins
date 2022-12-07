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

package io.qalipsis.plugins.jms.producer

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.Tags
import io.qalipsis.api.events.EventsLogger
import io.qalipsis.api.meters.CampaignMeterRegistry
import java.util.concurrent.ConcurrentHashMap
import javax.jms.Connection
import javax.jms.Destination
import javax.jms.Message
import javax.jms.MessageProducer
import javax.jms.Session

/**
 * JMS producer client to produce native JMS [Message]s to a JMS server.
 *
 * @property connectionFactory supplier for the JMS [Connection]
 * @property metrics the metrics for the produce operation
 * @property converter from a [JmsProducerRecord] to a native JMS [Message]
 *
 * @author Alexander Sosnovsky
 */
internal class JmsProducer(
    private val connectionFactory: () -> Connection,
    private val converter: JmsProducerConverter,
    private val eventsLogger: EventsLogger?,
    private val meterRegistry: CampaignMeterRegistry?
) {

    private lateinit var connection: Connection

    private var running = false

    private val producers = ConcurrentHashMap<Destination, MessageProducer>()

    private lateinit var session: Session

    private val eventPrefix = "jms.produce"

    private val meterPrefix = "jms-produce"

    private var recordsToProduce: Counter? = null

    private var producedBytesCounter: Counter? = null

    private var producedRecordsCounter: Counter? = null

    /**
     * Prepares producer inside before execute.
     */
    fun start(contextMetersTags: Tags) {
        meterRegistry?.apply {
            recordsToProduce = counter("$meterPrefix-producing-records", contextMetersTags)
            producedBytesCounter = counter("$meterPrefix-produced-value-bytes", contextMetersTags)
            producedRecordsCounter = counter("$meterPrefix-produced-records", contextMetersTags)
        }
        running = true
        connection = connectionFactory()
        connection.start()

        session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE)

        producers.clear()
    }

    /**
     * Executes producing [Message]s to JMS server.
     */
    fun execute(
        messages: List<JmsProducerRecord>,
        contextEventTags: Map<String, String>
    ): JmsProducerMeters {
        val metersForCall = JmsProducerMeters(messages.size)
        recordsToProduce?.increment(messages.size.toDouble())
        eventsLogger?.debug("${eventPrefix}.producing.records", messages.size, tags = contextEventTags)

        var sentRecords = 0
        messages.forEach { m ->
            kotlin.runCatching {
                producers.computeIfAbsent(m.destination) { destination ->
                    session.createProducer(destination)
                }.run {
                    val message = converter.convert(m, session)
                    send(message)

                    sentRecords++
                    metersForCall.producedBytes += when (m.messageType) {
                        JmsMessageType.TEXT -> "${m.value}".toByteArray().size
                        JmsMessageType.BYTES -> (m.value as? ByteArray)?.size ?: 0
                        else -> 0
                    }
                }
            }
        }
        eventsLogger?.info("${eventPrefix}.produced.records", sentRecords, tags = contextEventTags)
        eventsLogger?.info("${eventPrefix}.produced.bytes", metersForCall.producedBytes, tags = contextEventTags)

        metersForCall.producedRecords = sentRecords
        producedBytesCounter?.increment(metersForCall.producedBytes.toDouble())
        producedRecordsCounter?.increment(sentRecords.toDouble())

        return metersForCall
    }

    /**
     * Shutdown producer after execute.
     */
    fun stop() {
        meterRegistry?.apply {
            remove(recordsToProduce!!)
            remove(producedBytesCounter!!)
            remove(producedRecordsCounter!!)
            recordsToProduce = null
            producedBytesCounter = null
            producedRecordsCounter = null
        }
        running = false
        producers.forEach { it.value.close() }
        producers.clear()
        connection.stop()
    }

}
