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

package io.qalipsis.plugins.jakarta.consumer

import io.qalipsis.api.context.StepOutput
import io.qalipsis.api.context.StepStartStopContext
import io.qalipsis.api.events.EventsLogger
import io.qalipsis.api.meters.CampaignMeterRegistry
import io.qalipsis.api.meters.Counter
import io.qalipsis.api.report.ReportMessageSeverity
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
    private val meterRegistry: CampaignMeterRegistry?,
    private val eventsLogger: EventsLogger?
) : DatasourceObjectConverter<Message, JakartaConsumerResult<O>> {

    private val eventPrefix = "jakarta.consume"
    private val meterPrefix = "jakarta-consume"
    private var consumedBytesCounter: Counter? = null
    private var consumedRecordsCounter: Counter? = null
    private lateinit var eventTags: Map<String, String>

    override fun start(context: StepStartStopContext) {
        eventTags = context.toEventTags()
        val scenarioName = context.scenarioName
        val stepName = context.stepName
        meterRegistry?.apply {
            consumedBytesCounter =
                counter(scenarioName, stepName, "$meterPrefix-value-bytes", context.toMetersTags()).report {
                display(
                    format = "received: %,.0f bytes",
                    severity = ReportMessageSeverity.INFO,
                    row = 1,
                    column = 0,
                    Counter::count
                )
            }
            consumedRecordsCounter =
                counter(scenarioName, stepName, "$meterPrefix-records", context.toMetersTags()).report {
                display(
                    format = "received rec: %,.0f",
                    severity = ReportMessageSeverity.INFO,
                    row = 1,
                    column = 1,
                    Counter::count
                )
            }
        }
    }

    override fun stop(context: StepStartStopContext) {
        meterRegistry?.apply {
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
