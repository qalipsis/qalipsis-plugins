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

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.Tags
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.context.StepName
import io.qalipsis.api.context.StepStartStopContext
import io.qalipsis.api.events.EventsLogger
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.meters.CampaignMeterRegistry
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

    private val eventPrefix = "rabbitmq-produce.${stepName}"

    private var meterBytesCounter: Counter? = null

    private var meterRecordsCounter: Counter? = null

    private var meterFailedBytesCounter: Counter? = null

    private var meterFailedRecordsCounter: Counter? = null

    private var meterTags: Tags? = null

    private var eventTags: Map<String, String>? = null


    override suspend fun start(context: StepStartStopContext) {
        meterTags = context.toMetersTags()
        eventTags = context.toEventTags()

        initMonitoringMetrics()

        rabbitMqProducer.start()
    }

    private fun initMonitoringMetrics() {
        meterRegistry?.apply {
            meterBytesCounter = counter("${eventPrefix}-bytes", meterTags!!)
            meterRecordsCounter = counter("${eventPrefix}-records", meterTags!!)
            meterFailedBytesCounter = counter("${eventPrefix}-failed-bytes", meterTags!!)
            meterFailedRecordsCounter = counter("${eventPrefix}-failed-records", meterTags!!)
        }

    }

    override suspend fun execute(context: StepContext<I, RabbitMqProducerResult<I>>) {
        val input = context.receive()

        val messages = recordFactory(context, input)
        val executionStart = System.nanoTime()

        try {
            rabbitMqProducer.execute(messages)

            val executionTime = System.nanoTime() - executionStart
            eventsLogger?.info(
                "${eventPrefix}.success-response-time",
                arrayOf(executionTime, messages),
                tags = eventTags!!
            )

            messages.forEach {
                meterBytesCounter?.increment(it.value.size.toDouble())
                meterRecordsCounter?.increment()
            }
        } catch (e: Exception) {
            val executionTime = System.nanoTime() - executionStart
            eventsLogger?.warn(
                "${eventPrefix}.failure-response-time",
                arrayOf(executionTime, messages),
                tags = eventTags!!
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
            meterBytesCounter?.let { remove(it) }
            meterRecordsCounter?.let { remove(it) }
            meterFailedBytesCounter?.let { remove(it) }
            meterFailedRecordsCounter?.let { remove(it) }
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
