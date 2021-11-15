package io.qalipsis.plugins.redis.lettuce

import io.micrometer.core.instrument.MeterRegistry
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.context.StepError
import io.qalipsis.api.events.EventsLogger
import io.qalipsis.plugins.redis.lettuce.save.LettuceSaveResult
import io.qalipsis.plugins.redis.lettuce.streams.producer.LettuceStreamsProducerResult
import java.time.Duration


internal class LettuceMonitoringCollector(
    private val stepContext: StepContext<*, *>,
    private val eventsLogger: EventsLogger?,
    private val meterRegistry: MeterRegistry?,
    stepQualifier: String
) : MonitoringCollector {

    private var sendingFailures: MutableList<Throwable>? = mutableListOf()

    private val meters = MetersImpl()

    private val eventPrefix = "lettuce.${stepQualifier}"

    private val metersPrefix = "lettuce-${stepQualifier}"

    override fun recordSendingData(bytesToBeSent: Int) {
        meters.bytesToBeSent += bytesToBeSent
        eventsLogger?.info("${eventPrefix}.sending.bytes", bytesToBeSent, tags = stepContext.toEventTags())
        meterRegistry?.counter("${metersPrefix}-sending-bytes", stepContext.toMetersTags())?.increment(bytesToBeSent.toDouble())
    }

    override fun recordSentDataSuccess(timeToSent: Duration, sentBytes: Int) {
        meters.sentBytes += sentBytes
        eventsLogger?.info(
            "${eventPrefix}.sent.bytes",
            arrayOf(timeToSent, sentBytes),
            tags = stepContext.toEventTags()
        )
        meterRegistry?.counter("${metersPrefix}-sent-bytes", stepContext.toMetersTags())?.increment(sentBytes.toDouble())
    }

    override fun recordSentDataFailure(timeToFailure: Duration, throwable: Throwable) {
        sendingFailures?.add(throwable)
        eventsLogger?.warn(
            "${eventPrefix}.sending.failed",
            arrayOf(timeToFailure, throwable),
            tags = stepContext.toEventTags()
        )
        meterRegistry?.counter("${metersPrefix}-sending-failure", stepContext.toMetersTags())?.increment()

        stepContext.addError(StepError(throwable))
    }


    fun <IN> toResult(input: IN) = LettuceStreamsProducerResult(
        input,
        sendingFailures,
        meters
    )

    fun <IN> toSaveResult(input: IN) = LettuceSaveResult(
        input,
        sendingFailures,
        meters
    )

}
