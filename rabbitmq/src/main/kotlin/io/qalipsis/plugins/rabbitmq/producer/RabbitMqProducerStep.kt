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

package io.qalipsis.plugins.rabbitmq.producer

import io.qalipsis.api.context.ScenarioName
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.context.StepName
import io.qalipsis.api.context.StepStartStopContext
import io.qalipsis.api.events.EventsLogger
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.meters.CampaignMeterRegistry
import io.qalipsis.api.meters.Counter
import io.qalipsis.api.report.ReportMessageSeverity
import io.qalipsis.api.retry.RetryPolicy
import io.qalipsis.api.steps.AbstractStep


/**
 * Implementation of a [io.qalipsis.api.steps.Step] able to produce messages to the RabbitMQ broker.
 *
 * @property rabbitMqProducer producer to use to execute the producing for the current step
 * @property recordFactory closure to generate list of [RabbitMqProducerRecord]
 * @property eventsLogger to log events
 *
 * @author Alexander Sosnovsky
 */
internal class RabbitMqProducerStep<I>(
    stepName: StepName,
    retryPolicy: RetryPolicy?,
    private val rabbitMqProducer: RabbitMqProducer,
    private val recordFactory: (suspend (ctx: StepContext<*, *>, input: I) -> List<RabbitMqProducerRecord>),
    val meterRegistry: CampaignMeterRegistry? = null,
    val eventsLogger: EventsLogger? = null
) : AbstractStep<I, RabbitMqProducerResult<I>>(stepName, retryPolicy) {

    private val eventPrefix = "rabbitmq.produce"

    private val meterPrefix = "rabbitmq-produce"

    private var meterBytesCounter: Counter? = null

    private var meterRecordsCounter: Counter? = null

    private var successRecordsCounter: Counter? = null

    private var successByteCounter: Counter? = null

    private var meterFailedBytesCounter: Counter? = null

    private var meterFailedRecordsCounter: Counter? = null

    private lateinit var eventTags: Map<String, String>


    override suspend fun start(context: StepStartStopContext) {
        eventTags = context.toEventTags()
        val scenarioName = context.scenarioName
        val stepName = context.stepName
        initMonitoringMetrics(scenarioName, stepName)

        rabbitMqProducer.start()
    }

    private fun initMonitoringMetrics(scenarioName: ScenarioName, stepName: StepName) {
        meterRegistry?.apply {
            meterBytesCounter = counter(scenarioName, stepName, "${meterPrefix}-bytes", eventTags).report {
                display(
                    format = "attempted %,.0f bytes",
                    severity = ReportMessageSeverity.INFO,
                    row = 0,
                    column = 1,
                    Counter::count
                )
            }
            meterRecordsCounter = counter(scenarioName, stepName,"${meterPrefix}-records", eventTags).report {
                display(
                    format = "attempted rec %,.0f",
                    severity = ReportMessageSeverity.INFO,
                    row = 0,
                    column = 0,
                    Counter::count
                )
            }
            successByteCounter = counter(scenarioName, stepName, "${meterPrefix}-success-bytes", eventTags).report {
                display(
                    format = "\u2713 %,.0f bytes successes",
                    severity = ReportMessageSeverity.INFO,
                    row = 0,
                    column = 3,
                    Counter::count
                )
            }
            successRecordsCounter = counter(scenarioName, stepName,"${meterPrefix}-success-records", eventTags).report {
                display(
                    format = "\u2713 %,.0f successes",
                    severity = ReportMessageSeverity.INFO,
                    row = 0,
                    column = 2,
                    Counter::count
                )
            }
            meterFailedBytesCounter = counter(scenarioName, stepName,"${meterPrefix}-failed-bytes", eventTags)
            meterFailedRecordsCounter = counter(scenarioName, stepName,"${meterPrefix}-failed-records", eventTags).report {
                display(
                    format = "\u2716 %,.0f failures",
                    severity = ReportMessageSeverity.ERROR,
                    row = 0,
                    column = 4,
                    Counter::count
                )
            }
        }

    }

    override suspend fun execute(context: StepContext<I, RabbitMqProducerResult<I>>) {
        val input = context.receive()

        val messages = recordFactory(context, input)
        val executionStart = System.nanoTime()

        try {
            messages.forEach {
                meterBytesCounter?.increment(it.value.size.toDouble())
                meterRecordsCounter?.increment()
            }
            rabbitMqProducer.execute(messages)

            val executionTime = System.nanoTime() - executionStart
            eventsLogger?.info(
                "${eventPrefix}.success-response-time",
                arrayOf(executionTime, messages),
                tags = eventTags
            )

            messages.forEach {
                successByteCounter?.increment(it.value.size.toDouble())
                successRecordsCounter?.increment()
            }
        } catch (e: Exception) {
            val executionTime = System.nanoTime() - executionStart
            eventsLogger?.warn(
                "${eventPrefix}.failure-response-time",
                arrayOf(executionTime, messages),
                tags = eventTags
            )
            messages.forEach {
                meterFailedBytesCounter?.increment(it.value.size.toDouble())
                meterFailedRecordsCounter?.increment()
            }
            throw e
        }
        val result = RabbitMqProducerResult(input, messages)
        context.send(result)
    }

    override suspend fun stop(context: StepStartStopContext) {
        log.info { "Stopping the RabbitMQ producer" }
        rabbitMqProducer.stop()
        stopMonitoringMetrics()
        log.info { "RabbitMQ producer was stopped" }
    }

    private fun stopMonitoringMetrics() {
        meterRegistry?.apply {
            meterBytesCounter = null
            meterRecordsCounter = null
            meterFailedBytesCounter = null
            meterFailedRecordsCounter = null
        }
    }

    companion object {

        @JvmStatic
        private val log = logger()
    }

}
