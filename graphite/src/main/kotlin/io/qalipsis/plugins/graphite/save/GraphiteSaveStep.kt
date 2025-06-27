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
