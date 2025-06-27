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

package io.qalipsis.plugins.influxdb.poll

import com.influxdb.client.kotlin.InfluxDBClientKotlin
import com.influxdb.query.FluxRecord
import io.aerisconsulting.catadioptre.KTestable
import io.qalipsis.api.context.StepStartStopContext
import io.qalipsis.api.events.EventsLogger
import io.qalipsis.api.lang.concurrentList
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.meters.CampaignMeterRegistry
import io.qalipsis.api.meters.Counter
import io.qalipsis.api.meters.Timer
import io.qalipsis.api.report.ReportMessageSeverity
import io.qalipsis.api.steps.datasource.DatasourceIterativeReader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.time.Duration

/**
 * Database reader
 *
 * @property pollStatement statement to execute
 * @property pollDelay duration between the end of a poll and the start of the next one
 * @property resultsChannelFactory factory to create the channel containing the received results sets
 * @property running running state of the reader
 * @property pollingJob instance of the background job polling data from the database
 * @property eventsLogger the logger for events to track what happens during save query execution.
 * @property meterRegistry registry for the meters.
 *
 * @author Alex Averyanov
 */
internal class InfluxDbIterativeReader(
    private val coroutineScope: CoroutineScope,
    private val clientFactory: () -> InfluxDBClientKotlin,
    private val pollStatement: PollStatement,
    private val pollDelay: Duration,
    private val resultsChannelFactory: () -> Channel<InfluxDbQueryResult> = { Channel(Channel.UNLIMITED) },
    private val eventsLogger: EventsLogger?,
    private val meterRegistry: CampaignMeterRegistry?
) : DatasourceIterativeReader<InfluxDbQueryResult> {

    private val eventPrefix = "influxdb.poll"

    private val meterPrefix = "influxdb-poll"

    private var running = false

    private lateinit var client: InfluxDBClientKotlin

    private lateinit var pollingJob: Job

    private lateinit var resultsChannel: Channel<InfluxDbQueryResult>

    private lateinit var context: StepStartStopContext

    private var recordsCount: Counter? = null

    private var timeToResponse: Timer? = null

    private var failureCounter: Counter? = null

    private var successCounter: Counter? = null

    override fun start(context: StepStartStopContext) {
        log.debug { "Starting the step with the context $context" }
        meterRegistry?.apply {
            val tags = context.toMetersTags()
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
            timeToResponse = timer(scenarioName, stepName, "$meterPrefix-time-to-response", tags)
            failureCounter = counter(scenarioName, stepName, "$meterPrefix-failures", tags).report {
                display(
                    format = "\u2716 %,.0f failures",
                    severity = ReportMessageSeverity.ERROR,
                    row = 0,
                    column = 1,
                    Counter::count
                )
            }
            successCounter = counter(scenarioName, stepName, "$meterPrefix-successes", tags).report {
                display(
                    format = "\u2713 %,.0f successes",
                    severity = ReportMessageSeverity.INFO,
                    row = 1,
                    column = 0,
                    Counter::count
                )
            }
        }
        this.context = context
        running = true
        init()
        pollingJob = coroutineScope.launch {
            log.debug { "Polling job just started for context $context" }
            try {
                while (running) {
                    poll(client)
                    if (running) {
                        delay(pollDelay.toMillis())
                    }
                }
                log.debug { "Polling job just completed for context $context" }
            } finally {
                resultsChannel.cancel()
            }
        }
    }

    override fun stop(context: StepStartStopContext) {
        meterRegistry?.apply {
            recordsCount = null
            timeToResponse = null
            failureCounter = null
            successCounter = null
        }
        running = false
        runCatching {
            runBlocking {
                pollingJob.cancelAndJoin()
            }
        }
        runCatching {
            client.close()
        }
        resultsChannel.cancel()
        pollStatement.reset()
    }

    @KTestable
    fun init() {
        resultsChannel = resultsChannelFactory()
        client = clientFactory()
    }

    private suspend fun poll(client: InfluxDBClientKotlin) {
        val records = concurrentList<FluxRecord>()
        eventsLogger?.trace("$eventPrefix.polling", tags = context.toEventTags())
        val requestStart = System.nanoTime()
        try {
            val results = client.getQueryKotlinApi().query(pollStatement.getNextQuery())
            val timeToSuccess = Duration.ofNanos(System.nanoTime() - requestStart)
            for (record in results) {
                // FIXME The values are not really sorted.
                records.add(record)
            }
            recordsCount?.increment(records.size.toDouble())
            eventsLogger?.info(
                "$eventPrefix.successful-response",
                arrayOf(timeToSuccess, records.size),
                tags = context.toEventTags()
            )
            pollStatement.saveTiebreaker(records)
            resultsChannel.send(
                InfluxDbQueryResult(
                    results = records,
                    meters = InfluxDbQueryMeters(records.size, timeToSuccess)
                )
            )
            successCounter?.increment()
        } catch (e: InterruptedException) {
            // The exception is ignored.
        } catch (e: CancellationException) {
            // The exception is ignored.
        } catch (e: Exception) {
            val timeToFailure = Duration.ofNanos(System.nanoTime() - requestStart)
            failureCounter?.increment()
            eventsLogger?.warn(
                "$eventPrefix.failure",
                arrayOf(e, timeToFailure),
                tags = context.toEventTags()
            )
            log.debug(e) { e.message }
        }
    }

    override suspend fun hasNext(): Boolean = running

    override suspend fun next(): InfluxDbQueryResult = resultsChannel.receive()

    private companion object {
        val log = logger()
    }
}