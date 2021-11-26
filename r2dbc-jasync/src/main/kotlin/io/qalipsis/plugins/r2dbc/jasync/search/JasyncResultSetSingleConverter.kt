package io.qalipsis.plugins.r2dbc.jasync.search

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.qalipsis.api.context.StepOutput
import io.qalipsis.api.context.StepStartStopContext
import io.qalipsis.api.events.EventsLogger
import io.qalipsis.api.lang.tryAndLogOrNull
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.plugins.r2dbc.jasync.converters.JasyncResultSetConverter
import io.qalipsis.plugins.r2dbc.jasync.converters.ResultValuesConverter
import java.util.concurrent.atomic.AtomicLong

/**
 * Implementation of [JasyncResultSetConverter], to convert the whole result set into single records.
 *
 * @author Fiodar Hmyza
 */
internal class JasyncResultSetSingleConverter<I>(
    private val resultValuesConverter: ResultValuesConverter,
    private val meterRegistry: MeterRegistry?,
    private val eventsLogger: EventsLogger?
) : JasyncResultSetConverter<ResultSetWrapper, JasyncSearchSingleResult<I, *>, I> {

    private val eventPrefix: String = "r2dbc.jasync.search"

    private val meterPrefix: String = "r2dbc-jasync-search"

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
        value: ResultSetWrapper,
        input: I,
        output: StepOutput<JasyncSearchSingleResult<I,*>>
    ) {
        eventsLogger?.info("${eventPrefix}.records", value.resultSet.size, tags = eventTags)
        recordsCounter?.increment(value.resultSet.size.toDouble())

        tryAndLogOrNull(log) {
            value.resultSet.map { row ->
                val jasyncSearchResult = JasyncSearchRecord(
                    offset.getAndIncrement(),
                    value.resultSet.columnNames().map { column ->
                        column to resultValuesConverter.process(row[column])
                    }.toMap()
                )
                JasyncSearchSingleResult(
                    input = input,
                    record = jasyncSearchResult,
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
