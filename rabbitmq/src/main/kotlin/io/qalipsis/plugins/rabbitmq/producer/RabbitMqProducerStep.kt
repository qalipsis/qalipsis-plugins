package io.qalipsis.plugins.rabbitmq.producer

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tags
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.context.StepId
import io.qalipsis.api.context.StepStartStopContext
import io.qalipsis.api.events.EventsLogger
import io.qalipsis.api.logging.LoggerHelper.logger
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
    stepId: StepId,
    retryPolicy: RetryPolicy?,
    private val rabbitMqProducer: RabbitMqProducer,
    private val recordFactory: (suspend (ctx: StepContext<*, *>, input: I) -> List<RabbitMqProducerRecord>),
    val rabbitMqProducerResult: RabbitMqProducerResult,
    val meterRegistry: MeterRegistry? = null,
    val eventsLogger: EventsLogger? = null
) : AbstractStep<I, RabbitMqProducerResult>(stepId, retryPolicy) {

    private val eventPrefix = "rabbitmq-produce.${stepId}"

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
            meterBytesCounter = counter("${eventPrefix}-bytes", meterTags)
            meterRecordsCounter = counter("${eventPrefix}-records", meterTags)
            meterFailedBytesCounter = counter("${eventPrefix}-failed-bytes", meterTags)
            meterFailedRecordsCounter = counter("${eventPrefix}-failed-records", meterTags)
        }

    }

    override suspend fun execute(context: StepContext<I, RabbitMqProducerResult>) {
        val input = context.receive()

        val messages = recordFactory(context, input)

        rabbitMqProducerResult.records = messages

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
            throw RuntimeException(e)
        }
        context.send(rabbitMqProducerResult)
    }

    override suspend fun stop(context: StepStartStopContext) {
        log.info { "Stopping the RabbitMQ producer" }
        rabbitMqProducer.stop()
        stopMonitoringMetrics()
        log.info { "RabbitMQ producer was stopped" }
    }

    private fun stopMonitoringMetrics() {
        meterRegistry?.apply {
            remove(meterBytesCounter)
            remove(meterRecordsCounter)
            remove(meterFailedBytesCounter)
            remove(meterFailedRecordsCounter)
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
