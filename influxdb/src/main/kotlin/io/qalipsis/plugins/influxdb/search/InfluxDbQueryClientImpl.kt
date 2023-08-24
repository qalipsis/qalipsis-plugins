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

package io.qalipsis.plugins.influxdb.search

import com.influxdb.client.kotlin.InfluxDBClientKotlin
import com.influxdb.client.kotlin.QueryKotlinApi
import com.influxdb.query.FluxRecord
import io.qalipsis.api.context.StepStartStopContext
import io.qalipsis.api.events.EventsLogger
import io.qalipsis.api.lang.tryAndLog
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.meters.CampaignMeterRegistry
import io.qalipsis.api.meters.Counter
import io.qalipsis.api.meters.Timer
import io.qalipsis.api.report.ReportMessageSeverity
import io.qalipsis.plugins.influxdb.poll.InfluxDbQueryMeters
import io.qalipsis.plugins.influxdb.poll.InfluxDbQueryResult
import kotlinx.coroutines.CancellationException
import java.time.Duration
import java.util.concurrent.TimeUnit


/**
 * Implementation of [InfluxDbQueryClient].
 * Client to query from InfluxDB.
 *
 * @property clientFactory supplier for the InfluxDb client
 * @property eventsLogger the logger for events to track what happens during save query execution.
 * @property meterRegistry registry for the meters.
 *
 * @author Palina Bril
 */
internal class InfluxDbQueryClientImpl(
    private val clientFactory: () -> InfluxDBClientKotlin,
    private var eventsLogger: EventsLogger?,
    private val meterRegistry: CampaignMeterRegistry?
) : InfluxDbQueryClient {

    private lateinit var client: InfluxDBClientKotlin

    private lateinit var queryClient: QueryKotlinApi

    private val eventPrefix = "influxdb.search"

    private val meterPrefix = "influxdb-search"

    private var recordsCount: Counter? = null

    private var timeToResponse: Timer? = null

    private var successCounter: Counter? = null

    private var failureCounter: Counter? = null

    override suspend fun start(context: StepStartStopContext) {
        client = clientFactory()
        queryClient = client.getQueryKotlinApi()
        meterRegistry?.apply {
            val tags = context.toEventTags()
            val scenarioName = context.scenarioName
            val stepName = context.stepName
            recordsCount = counter(scenarioName, stepName, "$meterPrefix-received-records", tags).report {
                display(
                    format = "attempted req: %,.0f",
                    severity = ReportMessageSeverity.INFO,
                    row = 0,
                    column = 0,
                    Counter::count
                )
            }
            timeToResponse = timer(scenarioName, stepName,"$meterPrefix-time-to-response", tags)
            successCounter = counter(scenarioName, stepName,"$meterPrefix-success", tags).report {
                display(
                    format = "\u2713 %,.0f successes",
                    severity = ReportMessageSeverity.INFO,
                    row = 1,
                    column = 0,
                    Counter::count
                )
            }
            failureCounter = counter(scenarioName, stepName,"$meterPrefix-failure", tags).apply {
                report {
                    display(
                        format = "\u2716 %,.0f failures",
                        severity = ReportMessageSeverity.ERROR,
                        row = 0,
                        column = 1,
                        Counter::count
                    )
                }
            }
        }
    }

    /**
     * Executes a query and returns the list of results.
     */
    override suspend fun execute(
        query: String,
        contextEventTags: Map<String, String>
    ): InfluxDbQueryResult {
        val records = mutableListOf<FluxRecord>()
        eventsLogger?.debug("$eventPrefix.searching", tags = contextEventTags)
        val requestStart = System.nanoTime()
        var duration: Duration = Duration.ZERO
        try {
            val results = queryClient.query(query)
            duration = Duration.ofNanos(System.nanoTime() - requestStart)
            for (record in results) {
                records.add(record)
            }
            recordsCount?.increment(records.size.toDouble())
            timeToResponse?.record(duration.toNanos(), TimeUnit.NANOSECONDS)
            eventsLogger?.info(
                "$eventPrefix.success",
                arrayOf(duration, records.size), tags = contextEventTags
            )
        } catch (e: InterruptedException) {
            // The exception is ignored.
        } catch (e: CancellationException) {
            // The exception is ignored.
        } catch (e: Exception) {
            duration = Duration.ofNanos(System.nanoTime() - requestStart)
            eventsLogger?.warn("$eventPrefix.failure", arrayOf(e, duration), tags = contextEventTags)
            failureCounter?.increment()
            log.debug(e) { e.message }
            throw e
        }
        return InfluxDbQueryResult(
            records,
            InfluxDbQueryMeters(records.size, duration)
        )
    }

    /**
     * Shutdown client after execute
     */
    override suspend fun stop(context: StepStartStopContext) {
        meterRegistry?.apply {
            recordsCount = null
            timeToResponse = null
            successCounter = null
            failureCounter = null
        }
        tryAndLog(log) {
            client.close()
        }
    }

    companion object {
        @JvmStatic
        private val log = logger()
    }
}
