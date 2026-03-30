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

package io.qalipsis.plugins.r2dbc.jasync.poll

import com.github.jasync.sql.db.ResultSet
import io.qalipsis.api.context.MonitoringTags
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
 * Implementation of [DatasourceObjectConverter], to convert the whole result set into a unique record.
 *
 * @author Eric Jessé
 */
internal class ResultSetBatchConverter(
    private val resultValuesConverter: ResultValuesConverter,
    private val meterRegistry: CampaignMeterRegistry?,
    private val eventsLogger: EventsLogger?
) : DatasourceObjectConverter<ResultSet, JasyncPollResults<*>> {

    private val eventPrefix: String = "r2dbc.jasync.poll"

    private val meterPrefix: String = "r2dbc-jasync-poll"

    private var recordsCounter: Counter? = null

    private var successCounter: Counter? = null

    private var failureCounter: Counter? = null

    override fun start(context: StepStartStopContext) {
        meterRegistry?.apply {
            val tags = context.toMetersTags()
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
        output: StepOutput<JasyncPollResults<*>>
    ) {
        val eventsTags = (output as? MonitoringTags)?.toEventTags().orEmpty()
        eventsLogger?.info("${eventPrefix}.records", value.size, tags = eventsTags)
        recordsCounter?.increment(value.size.toDouble())
        val jasyncPollMeters = JasyncPollMeters(value.size)

        val jasyncPollResultsList: List<DatasourceRecord<Map<String, Any?>>> = value.map { row ->
            DatasourceRecord(
                offset.getAndIncrement(),
                value.columnNames().map { column ->
                    column to resultValuesConverter.process(row[column])
                }.toMap()
            )
        }
        try {
            output.send(
                JasyncPollResults(
                    jasyncPollResultsList,
                    jasyncPollMeters
                )
            )
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
