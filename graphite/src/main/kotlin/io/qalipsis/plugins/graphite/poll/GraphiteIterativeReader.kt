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

package io.qalipsis.plugins.graphite.poll

import io.aerisconsulting.catadioptre.KTestable
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.Timer
import io.qalipsis.api.context.StepStartStopContext
import io.qalipsis.api.events.EventsLogger
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.meters.CampaignMeterRegistry
import io.qalipsis.api.steps.datasource.DatasourceIterativeReader
import io.qalipsis.plugins.graphite.render.service.GraphiteRenderApiService
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
 * @author Sandro Mamukelashvili
 */
internal class GraphiteIterativeReader(
    private val coroutineScope: CoroutineScope,
    private val clientFactory: () -> GraphiteRenderApiService,
    private val pollStatement: GraphitePollStatement,
    private val pollDelay: Duration,
    private val resultsChannelFactory: () -> Channel<GraphiteQueryResult> = { Channel(Channel.UNLIMITED) },
    private val eventsLogger: EventsLogger?,
    private val meterRegistry: CampaignMeterRegistry?
) : DatasourceIterativeReader<GraphiteQueryResult> {

    private val eventPrefix = "graphite.poll"

    private val meterPrefix = "graphite-poll"

    private var running = false

    private lateinit var client: GraphiteRenderApiService

    private lateinit var pollingJob: Job

    private lateinit var resultsChannel: Channel<GraphiteQueryResult>

    private lateinit var context: StepStartStopContext

    private var recordsCount: Counter? = null

    private var timeToResponse: Timer? = null

    private var failureCounter: Counter? = null

    override fun start(context: StepStartStopContext) {
        log.debug { "Starting the step with the context $context" }
        meterRegistry?.apply {
            val tags = context.toMetersTags()
            recordsCount = counter("$meterPrefix-received-records", tags)
            timeToResponse = timer("$meterPrefix-time-to-response", tags)
            failureCounter = counter("$meterPrefix-failures", tags)
        }
        this.context = context
        init()
        running = true
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
            remove(recordsCount!!)
            remove(timeToResponse!!)
            remove(failureCounter!!)
            recordsCount = null
            timeToResponse = null
            failureCounter = null
        }
        running = false
        runCatching {
            runBlocking {
                pollingJob.cancelAndJoin()
            }
        }
        resultsChannel.cancel()
        pollStatement.reset()
        client.close()
    }

    @KTestable
    fun init() {
        resultsChannel = resultsChannelFactory()
        client = clientFactory()
    }

    private suspend fun poll(client: GraphiteRenderApiService) {
        eventsLogger?.debug("$eventPrefix.polling", tags = context.toEventTags())
        val requestStart = System.nanoTime()
        try {
            val query = pollStatement.getNextQuery()
            log.info("Query : $query")
            val records = client.getAsJson(query)
            val timeToSuccess = Duration.ofNanos(System.nanoTime() - requestStart)
            recordsCount?.increment(records.size.toDouble())
            eventsLogger?.info(
                "$eventPrefix.successful-response", arrayOf(timeToSuccess, records.size), tags = context.toEventTags()
            )
            pollStatement.saveTiebreaker(records)
            resultsChannel.send(
                GraphiteQueryResult(
                    results = records,
                    meters = GraphiteQueryMeters(records.size, timeToSuccess)
                )
            )
        } catch (e: InterruptedException) {
            // The exception is ignored.
        } catch (e: CancellationException) {
            // The exception is ignored.
        } catch (e: Exception) {
            val timeToFailure = Duration.ofNanos(System.nanoTime() - requestStart)
            failureCounter?.increment()
            eventsLogger?.warn(
                "$eventPrefix.failure", arrayOf(e, timeToFailure), tags = context.toEventTags()
            )
            log.debug(e) { e.message }
        }
    }

    override suspend fun hasNext(): Boolean = running

    override suspend fun next(): GraphiteQueryResult = resultsChannel.receive()

    private companion object {
        val log = logger()
    }
}