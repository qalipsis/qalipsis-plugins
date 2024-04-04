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

import io.qalipsis.api.context.StepContext
import io.qalipsis.api.context.StepName
import io.qalipsis.api.context.StepStartStopContext
import io.qalipsis.api.events.EventsLogger
import io.qalipsis.api.lang.tryAndLog
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.meters.CampaignMeterRegistry
import io.qalipsis.api.meters.Counter
import io.qalipsis.api.meters.Timer
import io.qalipsis.api.report.ReportMessageSeverity
import io.qalipsis.api.retry.RetryPolicy
import io.qalipsis.api.steps.AbstractStep
import io.qalipsis.plugins.graphite.client.GraphiteClient
import io.qalipsis.plugins.graphite.client.GraphiteRecord
import java.time.Duration
import java.util.concurrent.TimeUnit

/**
 * Implementation of a [io.qalipsis.api.steps.Step] able to perform inserts into Graphite.
 *
 * @property clientBuilder builder for the client to send the records.
 * @property messageFactory closure to generate a list of messages.
 *
 * @author Palina Bril
 */
internal class GraphiteSaveStep<I>(
    id: StepName,
    retryPolicy: RetryPolicy?,
    private val clientBuilder: () -> GraphiteClient<GraphiteRecord>,
    private val eventsLogger: EventsLogger?,
    private val meterRegistry: CampaignMeterRegistry?,
    private val messageFactory: (suspend (ctx: StepContext<*, *>, input: I) -> Collection<GraphiteRecord>)
) : AbstractStep<I, GraphiteSaveResult<I>>(id, retryPolicy) {

    private lateinit var client: GraphiteClient<GraphiteRecord>

    private val eventPrefix = "graphite.save"

    private val meterPrefix = "graphite-save"

    private var messageCounter: Counter? = null

    private var timeToResponse: Timer? = null

    private var successCounter: Counter? = null

    override suspend fun start(context: StepStartStopContext) {
        client = clientBuilder()
        client.open()
        meterRegistry?.apply {
            val tags = context.toEventTags()
            val scenarioName = context.scenarioName
            val stepName = context.stepName
            messageCounter = counter(scenarioName, stepName, "$meterPrefix-saving-messages", tags).report {
                display(
                    format = "attempted req %,.0f",
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
                    column = 2,
                    Counter::count
                )
            }
        }
    }

    override suspend fun execute(context: StepContext<I, GraphiteSaveResult<I>>) {
        val input = context.receive()
        val messages = messageFactory(context, input)
        eventsLogger?.debug("$eventPrefix.saving-messages", messages.size, tags = context.toEventTags())
        messageCounter?.increment(messages.size.toDouble())

        val requestStart = System.nanoTime()
        client.send(messages)
        val timeToResponseNano = System.nanoTime() - requestStart
        val timeToResponse = Duration.ofNanos(timeToResponseNano)

        eventsLogger?.info("${eventPrefix}.time-to-response", timeToResponse, tags = context.toEventTags())
        eventsLogger?.info("${eventPrefix}.successes", messages.size, tags = context.toEventTags())
        successCounter?.increment(messages.size.toDouble())
        this.timeToResponse?.record(timeToResponseNano, TimeUnit.NANOSECONDS)

        context.send(
            GraphiteSaveResult(
                input, GraphiteSaveQueryMeters(
                    timeToResult = timeToResponse,
                    savedMessages = messages.size
                )
            )
        )
    }

    override suspend fun stop(context: StepStartStopContext) {
        meterRegistry?.apply {
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
