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

package io.qalipsis.plugins.redis.lettuce.streams.consumer

import io.lettuce.core.StreamMessage
import io.qalipsis.api.context.StepOutput
import io.qalipsis.api.context.StepStartStopContext
import io.qalipsis.api.lang.tryAndLogOrNull
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.meters.CampaignMeterRegistry
import io.qalipsis.api.meters.Counter
import io.qalipsis.api.report.ReportMessageSeverity
import io.qalipsis.api.steps.datasource.DatasourceObjectConverter
import java.util.concurrent.atomic.AtomicLong

/**
 * Implementation of [DatasourceObjectConverter], that reads a message of native Redis Streams records and forwards
 * each of them converted as [LettuceStreamsConsumedRecord].
 *
 * @author Gabriel Moraes
 */
internal class LettuceStreamsConsumerSingleConverter(
    private val meterRegistry: CampaignMeterRegistry?
) : DatasourceObjectConverter<List<StreamMessage<ByteArray, ByteArray>>, LettuceStreamsConsumedRecord> {

    private val meterPrefix = "redis-lettuce-streams-consumer"

    private var recordsCounter: Counter? = null

    private var valuesBytesReceived: Counter? = null

    override fun start(context: StepStartStopContext) {
        meterRegistry?.apply {
            val tags = context.toMetersTags()
            val scenarioName = context.scenarioName
            val stepName = context.stepName
            recordsCounter = counter(scenarioName, stepName, "$meterPrefix-records", tags).report {
                display(
                    format = "received rec: %,.0f",
                    severity = ReportMessageSeverity.INFO,
                    row = 1,
                    column = 0,
                    Counter::count
                )
            }
            valuesBytesReceived = counter(scenarioName, stepName, "$meterPrefix-records-bytes", tags).report {
                display(
                    format = "received: %,.0f bytes",
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
            recordsCounter = null
            valuesBytesReceived = null
        }
    }

    override suspend fun supply(
        offset: AtomicLong, value: List<StreamMessage<ByteArray, ByteArray>>,
        output: StepOutput<LettuceStreamsConsumedRecord>
    ) {
        value.forEach { record ->

            valuesBytesReceived?.increment(record.body.values.sumOf { it.size }.toDouble())
            recordsCounter?.increment()

            tryAndLogOrNull(log) {
                output.send(
                    LettuceStreamsConsumedRecord(
                        offset.getAndIncrement(),
                        record.id,
                        record.stream,
                        record.body.map { it.key.decodeToString() to it.value.decodeToString() }.toMap()
                    )
                )
            }
        }
    }

    companion object {

        @JvmStatic
        private val log = logger()
    }
}
