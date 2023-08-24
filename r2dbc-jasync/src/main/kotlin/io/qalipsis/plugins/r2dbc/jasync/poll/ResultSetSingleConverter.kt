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

package io.qalipsis.plugins.r2dbc.jasync.poll

import com.github.jasync.sql.db.ResultSet
import io.qalipsis.api.context.StepOutput
import io.qalipsis.api.context.StepStartStopContext
import io.qalipsis.api.events.EventsLogger
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.meters.CampaignMeterRegistry
import io.qalipsis.api.meters.Counter
import io.qalipsis.api.report.ReportMessageSeverity
import io.qalipsis.api.steps.datasource.DatasourceObjectConverter
import io.qalipsis.api.steps.datasource.DatasourceRecord
import io.qalipsis.plugins.r2dbc.jasync.converters.ResultValuesConverter
import java.util.concurrent.atomic.AtomicLong

/**
 *
 * @author Eric Jessé
 */
internal class ResultSetSingleConverter(
    private val resultValuesConverter: ResultValuesConverter,
    private val meterRegistry: CampaignMeterRegistry?,
    private val eventsLogger: EventsLogger?
) : DatasourceObjectConverter<ResultSet, DatasourceRecord<Map<String, Any?>>> {

    private val eventPrefix: String = "r2dbc.jasync.poll"

    private val meterPrefix: String = "r2dbc-jasync-poll"

    private var recordsCounter: Counter? = null

    private var successCounter: Counter? = null

    private var failureCounter: Counter? = null

    private lateinit var eventTags: Map<String, String>

    override fun start(context: StepStartStopContext) {
        meterRegistry?.apply {
            val tags = context.toEventTags()
            recordsCounter = counter(context.scenarioName, context.stepName, "$meterPrefix-records", tags).report {
                display(
                    format = "attempted req %,.0f",
                    severity = ReportMessageSeverity.INFO,
                    row = 0,
                    column = 0,
                    Counter::count
                )
            }
            failureCounter = counter(context.scenarioName, context.stepName, "$meterPrefix-failures", tags).report {
                display(
                    format = "\u2716 %,.0f failures",
                    severity = ReportMessageSeverity.ERROR,
                    row = 0,
                    column = 1,
                    Counter::count
                )
            }
            successCounter = counter(context.scenarioName, context.stepName, "$meterPrefix-successes", tags).report {
                display(
                    format = "\u2713 %,.0f successes",
                    severity = ReportMessageSeverity.INFO,
                    row = 1,
                    column = 0,
                    Counter::count
                )
            }
        }
        eventTags = context.toEventTags()
    }

    override fun stop(context: StepStartStopContext) {
        meterRegistry?.apply {
            recordsCounter = null
            successCounter = null
            failureCounter = null
        }
    }

    override suspend fun supply(
        offset: AtomicLong,
        value: ResultSet,
        output: StepOutput<DatasourceRecord<Map<String, Any?>>>
    ) {
        eventsLogger?.info("${eventPrefix}.records", value.size, tags = eventTags)
        recordsCounter?.increment(value.size.toDouble())
        try {
            value.map { row ->
                DatasourceRecord(
                    offset.getAndIncrement(),
                    value.columnNames().map { column ->
                        column to resultValuesConverter.process(row[column])
                    }.toMap()
                )
            }.forEach {
                output.send(it)
            }
            successCounter?.increment()
        } catch (e: Exception) {
            log.error(e.message, e)
            failureCounter?.increment()
        }
    }

    companion object {

        @JvmStatic
        private val log = logger()
    }
}
