package io.qalipsis.plugins.r2dbc.jasync.poll

import com.github.jasync.sql.db.ResultSet
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.qalipsis.api.context.StepOutput
import io.qalipsis.api.context.StepStartStopContext
import io.qalipsis.api.events.EventsLogger
import io.qalipsis.api.lang.tryAndLogOrNull
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.steps.datasource.DatasourceObjectConverter
import io.qalipsis.api.steps.datasource.DatasourceRecord
import io.qalipsis.plugins.r2dbc.jasync.converters.ResultValuesConverter
import java.util.concurrent.atomic.AtomicLong

/**
 * Implementation of [DatasourceObjectConverter], to convert the whole result set into a unique record.
 *
 * @author Eric Jessé
 */
internal class ResultSetBatchConverter(
    private val resultValuesConverter: ResultValuesConverter,
    private val meterRegistry: MeterRegistry?,
    private val eventsLogger: EventsLogger?
) : DatasourceObjectConverter<ResultSet, JasyncPollResults<*>> {

    private val eventPrefix: String = "r2dbc.jasync.poll"

    private val meterPrefix: String = "r2dbc-jasync-poll"

    private var recordsCounter: Counter? = null

    private lateinit var eventTags: Map<String, String>

    override fun start(context: StepStartStopContext) {
        meterRegistry?.apply {
            val tags = context.toMetersTags()
            recordsCounter = counter("$meterPrefix-records", tags)
        }
        eventTags = context.toEventTags();
    }

    override fun stop(context: StepStartStopContext) {
        meterRegistry?.apply {
            remove(recordsCounter!!)
            recordsCounter = null
        }
    }

    override suspend fun supply(
        offset: AtomicLong,
        value: ResultSet,
        output: StepOutput<JasyncPollResults<*>>
    ) {
        eventsLogger?.info("${eventPrefix}.records", value.size, tags = eventTags)
        recordsCounter?.increment(value.size.toDouble())
        val jasyncPollMeters = JasyncPollMeters(value.size)

        val jasyncPollResultsList: List<DatasourceRecord<Map<String, Any?>>> = value.map { row ->
                DatasourceRecord(
                    offset.getAndIncrement(),
                    value.columnNames().map { column ->
                        column to resultValuesConverter.process(row[column])
                    }.toMap()
                )
        }
        tryAndLogOrNull(log) {
            output.send(
                JasyncPollResults(
                    jasyncPollResultsList,
                    jasyncPollMeters
                )
            )
        }
    }

    companion object {

        @JvmStatic
        private val log = logger()
    }
}
