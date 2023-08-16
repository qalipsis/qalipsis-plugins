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
 * it converted as a list of [LettuceStreamsConsumedRecord].
 *
 * @author Gabriel Moraes
 */
internal class LettuceStreamsConsumerBatchConverter(
    private val meterRegistry: CampaignMeterRegistry?
) : DatasourceObjectConverter<List<StreamMessage<ByteArray, ByteArray>>, LettuceStreamsConsumerResult> {

    private val meterPrefix = "redis-lettuce-streams-consumer"

    private var recordsCounter: Counter? = null

    private var valuesBytesReceived: Counter? = null

    override fun start(context: StepStartStopContext) {
        meterRegistry?.apply {
            val tags = context.toEventTags()
            val scenarioName = context.scenarioName
            val stepName = context.stepName
            recordsCounter = counter(scenarioName, stepName,"$meterPrefix-records", tags).report {
                display(
                    format = "received rec: %,.0f",
                    severity = ReportMessageSeverity.INFO,
                    row = 1,
                    column = 0,
                    Counter::count
                )
            }
            valuesBytesReceived = counter(scenarioName, stepName,"$meterPrefix-records-bytes", tags).report {
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
        output: StepOutput<LettuceStreamsConsumerResult>
    ) {

        tryAndLogOrNull(log) {
            output.send(
                LettuceStreamsConsumerResult(
                    records = value.map { record ->

                        valuesBytesReceived?.increment(record.body.values.sumOf { it.size }.toDouble())
                        recordsCounter?.increment()

                        LettuceStreamsConsumedRecord(
                            offset.getAndIncrement(),
                            record.id,
                            record.stream,
                            record.body.map { it.key.decodeToString() to it.value.decodeToString() }.toMap()
                        )
                    },
                    meters = LettuceStreamsConsumerMetrics(
                        recordsCounter,
                        valuesBytesReceived
                    )
                )
            )
        }
    }

    companion object {

        @JvmStatic
        private val log = logger()
    }
}
