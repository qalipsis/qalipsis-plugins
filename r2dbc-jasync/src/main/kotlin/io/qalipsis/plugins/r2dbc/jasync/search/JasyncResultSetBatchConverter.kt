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

package io.qalipsis.plugins.r2dbc.jasync.search

import io.micrometer.core.instrument.Counter
import io.qalipsis.api.context.StepOutput
import io.qalipsis.api.context.StepStartStopContext
import io.qalipsis.api.events.EventsLogger
import io.qalipsis.api.lang.tryAndLogOrNull
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.meters.CampaignMeterRegistry
import io.qalipsis.plugins.r2dbc.jasync.converters.JasyncResultSetConverter
import io.qalipsis.plugins.r2dbc.jasync.converters.ResultValuesConverter
import java.util.concurrent.atomic.AtomicLong

/**
 * Implementation of [JasyncResultSetConverter], to convert the whole result set into a unique record.
 *
 * @author Fiodar Hmyza
 */
internal class JasyncResultSetBatchConverter<I>(
    private val resultValuesConverter: ResultValuesConverter,
    private val meterRegistry: CampaignMeterRegistry?,
    private val eventsLogger: EventsLogger?
) : JasyncResultSetConverter<ResultSetWrapper, JasyncSearchBatchResults<I, *>, I> {

    private val eventPrefix: String = "r2dbc.jasync.search"

    private val meterPrefix: String = "r2dbc-jasync-search"

    private var recordsCounter: Counter? = null

    private lateinit var eventTags: Map<String, String>

    override fun start(context: StepStartStopContext) {
        meterRegistry?.apply {
            val tags = context.toMetersTags()
            recordsCounter = counter("$meterPrefix-records", tags)
        }
        eventTags = context.toEventTags()
    }

    override fun stop(context: StepStartStopContext) {
        meterRegistry?.apply {
            remove(recordsCounter!!)
            recordsCounter = null
        }
    }

    override suspend fun supply(
        offset: AtomicLong,
        value: ResultSetWrapper,
        input: I,
        output: StepOutput<JasyncSearchBatchResults<I, *>>
    ) {
        eventsLogger?.info("${eventPrefix}.records", value.resultSet.size, tags = eventTags)
        recordsCounter?.increment(value.resultSet.size.toDouble())

        val jasyncSearchResultsList: List<JasyncSearchRecord<Map<String, Any?>>> = value.resultSet.map{ row ->
            JasyncSearchRecord(
                offset.getAndIncrement(),
                value.resultSet.columnNames().map { column ->
                    column to resultValuesConverter.process(row[column])
                }.toMap()
            )
        }
        val jasyncSearchMeters = JasyncSearchMeters(
            recordsCounter = value.resultSet.size,
            timeToResponse = value.timeToResponse
        )
        tryAndLogOrNull(log) {
            output.send(
                JasyncSearchBatchResults (
                    input = input,
                    jasyncSearchResultsList,
                    jasyncSearchMeters
                )
            )
        }
    }

    companion object {

        @JvmStatic
        private val log = logger()
    }
}
