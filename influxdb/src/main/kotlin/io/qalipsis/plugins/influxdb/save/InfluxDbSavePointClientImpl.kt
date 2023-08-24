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

package io.qalipsis.plugins.influxdb.save

import com.influxdb.client.kotlin.InfluxDBClientKotlin
import com.influxdb.client.kotlin.WriteKotlinApi
import com.influxdb.client.write.Point
import io.qalipsis.api.context.StepStartStopContext
import io.qalipsis.api.events.EventsLogger
import io.qalipsis.api.lang.tryAndLog
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.meters.CampaignMeterRegistry
import io.qalipsis.api.meters.Counter
import io.qalipsis.api.meters.Timer
import io.qalipsis.api.report.ReportMessageSeverity
import java.time.Duration
import java.util.concurrent.TimeUnit

/**
 * Implementation of [InfluxDbSavePointClient].
 * Client to query to InfluxDB.
 *
 * @property clientBuilder supplier for the InfluxDb client.
 * @property eventsLogger the logger for events to track what happens during save step execution.
 * @property meterRegistry registry for the meters.
 *
 * @author Palina Bril
 */
internal class InfluxDbSavePointClientImpl(
    private val clientBuilder: () -> InfluxDBClientKotlin,
    private var eventsLogger: EventsLogger?,
    private val meterRegistry: CampaignMeterRegistry?
) : InfluxDbSavePointClient {

    private lateinit var client: InfluxDBClientKotlin

    private lateinit var writeApi: WriteKotlinApi

    private val eventPrefix = "influxdb.save"

    private val meterPrefix = "influxdb-save"

    private var pointsCounter: Counter? = null

    private var timeToResponse: Timer? = null

    private var successCounter: Counter? = null

    private var failureCounter: Counter? = null

    override suspend fun start(context: StepStartStopContext) {
        client = clientBuilder()
        writeApi = client.getWriteKotlinApi()
        val failures = (pointsCounter?.count()?.minus(successCounter?.count()!!))
        failures?.let { failureCounter?.increment(it) }
        meterRegistry?.apply {
            val tags = context.toEventTags()
            val scenarioName = context.scenarioName
            val stepName = context.stepName
            pointsCounter = counter(scenarioName, stepName, "$meterPrefix-saving-points", tags).report {
                display(
                    format = "attempted saves: %,.0f",
                    severity = ReportMessageSeverity.INFO,
                    row = 0,
                    column = 0,
                    Counter::count
                )
            }
            timeToResponse = timer(scenarioName, stepName, "$meterPrefix-time-to-response", tags)
            successCounter = counter(scenarioName, stepName, "$meterPrefix-successes", tags).report {
                display(
                    format = "\u2713 %,.0f successes",
                    severity = ReportMessageSeverity.INFO,
                    row = 0,
                    column = 1,
                    Counter::count
                )
                display(
                    format = "\u2716 %,.0f failures",
                    severity = ReportMessageSeverity.ERROR,
                    row = 0,
                    column = 2,
                )
                { ((pointsCounter?.count()?.minus(this.count())) ?: 0) }
            }
        }
    }

    override suspend fun execute(
        bucketName: String,
        orgName: String,
        points: List<Point>,
        contextEventTags: Map<String, String>
    ): InfluxDbSaveQueryMeters {
        eventsLogger?.debug("$eventPrefix.saving-points", points.size, tags = contextEventTags)
        pointsCounter?.increment(points.size.toDouble())
        val requestStart = System.nanoTime()
        writeApi.writePoints(bucket = bucketName, org = orgName, points = points)
        val timeToResponseNano = System.nanoTime() - requestStart
        val timeToResponse = Duration.ofNanos(timeToResponseNano)
        eventsLogger?.info("${eventPrefix}.time-to-response", timeToResponse, tags = contextEventTags)
        eventsLogger?.info("${eventPrefix}.successes", points.size, tags = contextEventTags)
        successCounter?.increment(points.size.toDouble())
        this.timeToResponse?.record(timeToResponseNano, TimeUnit.NANOSECONDS)
        return InfluxDbSaveQueryMeters(
            timeToResult = timeToResponse,
            savedPoints = points.size
        )
    }

    override suspend fun stop(context: StepStartStopContext) {
        meterRegistry?.apply {
            pointsCounter = null
            timeToResponse = null
            successCounter = null
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
