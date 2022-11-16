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

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.qalipsis.api.context.StepOutput
import io.qalipsis.api.context.StepStartStopContext
import io.qalipsis.api.events.EventsLogger
import io.qalipsis.api.steps.datasource.DatasourceObjectConverter
import io.qalipsis.plugins.jakarta.JakartaDeserializer
import jakarta.jms.BytesMessage
import jakarta.jms.Message
import jakarta.jms.TextMessage
import java.util.concurrent.atomic.AtomicLong

/**
 * Implementation of [DatasourceObjectConverter], that reads a message from Jakarta and forwards
 * it converted to as [JakartaConsumerConverter].
 *
 * @author Krawist Ngoben
 */
internal class JakartaConsumerConverter<O : Any?>(
    private val valueDeserializer: JakartaDeserializer<O>,
    private val meterRegistry: MeterRegistry?,
    private val eventsLogger: EventsLogger?
) : DatasourceObjectConverter<Message, JakartaConsumerResult<O>> {

    private val eventPrefix: String = "jakarta.consume"
    private val meterPrefix: String = "jakarta-consume"
    private var consumedBytesCounter: Counter? = null
    private var consumedRecordsCounter: Counter? = null

    private lateinit var eventTags: Map<String, String>

    override fun start(context: StepStartStopContext) {
        meterRegistry?.apply {
            val tags = context.toMetersTags()
            consumedBytesCounter = counter("$meterPrefix-value-bytes", tags)
            consumedRecordsCounter = counter("$meterPrefix-records", tags)
        }
        eventTags = context.toEventTags()
    }

    override fun stop(context: StepStartStopContext) {
        meterRegistry?.apply {
            remove(consumedBytesCounter!!)
            remove(consumedRecordsCounter!!)
            consumedBytesCounter = null
            consumedRecordsCounter = null
        }
    }

    override suspend fun supply(
        offset: AtomicLong, value: Message,
        output: StepOutput<JakartaConsumerResult<O>>
    ) {
        val jakartaConsumerMeters = JakartaConsumerMeters()
        eventsLogger?.info("${eventPrefix}.received.records", 1, tags = eventTags)
        consumedRecordsCounter?.increment()

        val bytesCount = when (value) {
            is TextMessage -> {
                value.text.toByteArray().size
            }

            is BytesMessage -> {
                value.bodyLength.toInt()
            }

            else -> {
                0
            }
        }

        consumedBytesCounter?.increment(bytesCount.toDouble())
        jakartaConsumerMeters.consumedBytes = bytesCount

        eventsLogger?.info("${eventPrefix}.received.value-bytes", bytesCount, tags = eventTags)
        output.send(
            JakartaConsumerResult(
                JakartaConsumerRecord(
                    offset.getAndIncrement(),
                    value,
                    valueDeserializer.deserialize(value)
                ),
                jakartaConsumerMeters
            )
        )
    }
}
