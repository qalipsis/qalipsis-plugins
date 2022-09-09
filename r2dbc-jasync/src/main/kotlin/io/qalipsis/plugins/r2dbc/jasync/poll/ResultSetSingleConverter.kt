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
 *
 * @author Eric Jessé
 */
internal class ResultSetSingleConverter(
    private val resultValuesConverter: ResultValuesConverter,
    private val meterRegistry: MeterRegistry?,
    private val eventsLogger: EventsLogger?
) : DatasourceObjectConverter<ResultSet, DatasourceRecord<Map<String, Any?>>> {

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
        output: StepOutput<DatasourceRecord<Map<String, Any?>>>
    ) {
        eventsLogger?.info("${eventPrefix}.records", value.size, tags = eventTags)
        recordsCounter?.increment(value.size.toDouble())

        tryAndLogOrNull(log) {
            value.map { row ->
                DatasourceRecord(
                    offset.getAndIncrement(),
                    value.columnNames().map { column ->
                        column to resultValuesConverter.process(row[column])
                    }.toMap()
                )
            }.forEach {
                output.send(it)
            }
        }
    }

    companion object {

        @JvmStatic
        private val log = logger()
    }
}
