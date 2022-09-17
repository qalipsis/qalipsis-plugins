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

package io.qalipsis.plugins.graphite.save

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import io.qalipsis.api.context.StepStartStopContext
import io.qalipsis.api.events.EventsLogger
import io.qalipsis.api.lang.tryAndLog
import io.qalipsis.api.logging.LoggerHelper.logger
import java.time.Duration
import java.util.concurrent.TimeUnit

/**
 * Implementation of [GraphiteSaveMessageClient].
 * Client to query to Graphite.
 *
 * @property clientBuilder supplier for the Graphite client.
 * @property eventsLogger the logger for events to track what happens during save step execution.
 * @property meterRegistry registry for the meters.
 *
 * @author Palina Bril
 */
internal class GraphiteSaveMessageClientImpl(
    private val clientBuilder: () -> GraphiteSaveClient,
    private val eventsLogger: EventsLogger?,
    private val meterRegistry: MeterRegistry?
) : GraphiteSaveMessageClient {

    private lateinit var client: GraphiteSaveClient

    private val eventPrefix = "graphite.save"

    private val meterPrefix = "graphite-save"

    private var messageCounter: Counter? = null

    private var timeToResponse: Timer? = null

    private var successCounter: Counter? = null

    override suspend fun start(context: StepStartStopContext) {
        client = clientBuilder()
        client.start()
        meterRegistry?.apply {
            val tags = context.toMetersTags()
            messageCounter = counter("$meterPrefix-saving-messages", tags)
            timeToResponse = timer("$meterPrefix-time-to-response", tags)
            successCounter = counter("$meterPrefix-successes", tags)
        }
    }

    override suspend fun execute(
        messages: List<String>,
        contextEventTags: Map<String, String>
    ): GraphiteSaveQueryMeters {
        eventsLogger?.debug("$eventPrefix.saving-messages", messages.size, tags = contextEventTags)
        messageCounter?.increment(messages.size.toDouble())

        val requestStart = System.nanoTime()
        client.publish(messages)
        val timeToResponseNano = System.nanoTime() - requestStart
        val timeToResponse = Duration.ofNanos(timeToResponseNano)

        eventsLogger?.info("${eventPrefix}.time-to-response", timeToResponse, tags = contextEventTags)
        eventsLogger?.info("${eventPrefix}.successes", messages.size, tags = contextEventTags)
        successCounter?.increment(messages.size.toDouble())
        this.timeToResponse?.record(timeToResponseNano, TimeUnit.NANOSECONDS)

        return GraphiteSaveQueryMeters(
            timeToResult = timeToResponse,
            savedMessages = messages.size
        )
    }

    override suspend fun stop(context: StepStartStopContext) {
        meterRegistry?.apply {
            remove(messageCounter!!)
            remove(timeToResponse!!)
            remove(successCounter!!)
            messageCounter = null
            timeToResponse = null
            successCounter = null
        }
        tryAndLog(log) {
            client.close()
        }
    }

    companion object {
        private val log = logger()
    }
}
