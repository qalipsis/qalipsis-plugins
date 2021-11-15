package io.qalipsis.plugins.jms.consumer

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.qalipsis.api.context.StepOutput
import io.qalipsis.api.context.StepStartStopContext
import io.qalipsis.api.events.EventsLogger
import io.qalipsis.api.steps.datasource.DatasourceObjectConverter
import io.qalipsis.plugins.jms.JmsDeserializer
import java.util.concurrent.atomic.AtomicLong
import javax.jms.BytesMessage
import javax.jms.Message
import javax.jms.TextMessage

/**
 * Implementation of [DatasourceObjectConverter], that reads a message from JMS and forwards
 * it converted to as [JmsConsumerRecord].
 *
 * @author Alexander Sosnovsky
 */
internal class JmsConsumerConverter<O : Any?>(
    private val valueDeserializer: JmsDeserializer<O>,
    private val meterRegistry: MeterRegistry?,
    private val eventsLogger: EventsLogger?
) : DatasourceObjectConverter<Message, JmsConsumerResult<O>> {

    private val eventPrefix: String = "jms.consume"
    private val meterPrefix: String = "jms-consume"
    private var consumedBytesCounter: Counter? = null
    private var consumedRecordsCounter: Counter? = null

    private lateinit var eventTags: Map<String, String>

    override fun start(context: StepStartStopContext) {
        meterRegistry?.apply {
            val tags = context.toMetersTags()
            consumedBytesCounter = counter("$meterPrefix-value-bytes", tags)
            consumedRecordsCounter = counter("$meterPrefix-records", tags)
        }
        eventTags = context.toEventTags();
    }

    override fun stop(context: StepStartStopContext) {
        meterRegistry?.apply {
            remove(consumedBytesCounter!!)
            remove(consumedRecordsCounter!!)
            consumedBytesCounter = null
            consumedRecordsCounter = null
        }
    }

    override suspend fun supply(
        offset: AtomicLong, value: Message,
        output: StepOutput<JmsConsumerResult<O>>
    ) {
        val jmsConsumerMeters = JmsConsumerMeters()
        eventsLogger?.info("${eventPrefix}.received.records", 1, tags = eventTags)
        consumedRecordsCounter?.increment()

        val bytesCount = if (value is TextMessage) {
            value.text.toByteArray().size
        } else if (value is BytesMessage) {
            value.bodyLength.toInt()
        } else {
            0
        }

        consumedBytesCounter?.increment(bytesCount.toDouble())
        jmsConsumerMeters.consumedBytes = bytesCount

        eventsLogger?.info("${eventPrefix}.received.value-bytes", bytesCount, tags = eventTags)
        output.send(
            JmsConsumerResult(
                JmsConsumerRecord(
                    offset.getAndIncrement(),
                    value,
                    valueDeserializer.deserialize(value)
                ),
                jmsConsumerMeters
            )
        )
    }
}
