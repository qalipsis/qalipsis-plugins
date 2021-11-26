package io.qalipsis.plugins.r2dbc.jasync.search

import com.github.jasync.sql.db.ResultSet
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.qalipsis.api.context.StepOutput
import io.qalipsis.api.context.StepStartStopContext
import io.qalipsis.api.events.EventsLogger
import io.qalipsis.api.lang.tryAndLogOrNull
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.plugins.r2dbc.jasync.converters.JasyncResultSetConverter
import io.qalipsis.plugins.r2dbc.jasync.converters.ResultValuesConverter
import java.time.Duration
import java.util.concurrent.atomic.AtomicLong

/**
 * Implementation of [JasyncResultSetConverter], to convert the whole result set into a unique record.
 *
 * @author Fiodar Hmyza
 */
internal class JasyncResultSetBatchConverter<I>(
    private val resultValuesConverter: ResultValuesConverter,
    private val meterRegistry: MeterRegistry?,
    private val eventsLogger: EventsLogger?
) : JasyncResultSetConverter<ResultSetWrapper, JasyncSearchBatchResults<I, *>, I> {

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
        output: StepOutput<JasyncSearchBatchResults<I, *>>
    ) {
        eventsLogger?.info("${eventPrefix}.records", value.resultSet.size, tags = eventTags)
        recordsCounter?.increment(value.resultSet.size.toDouble())

        val jasyncSearchResultsList: List<JasyncSearchRecord<Map<String, Any?>>> = value.resultSet.map{ row ->
            JasyncSearchRecord(
                offset.getAndIncrement(),
                value.resultSet.columnNames().map { column ->
                    column to resultValuesConverter.process(row[column])
                }.toMap()
            )
        }
        val jasyncSearchMeters = JasyncSearchMeters(
            recordsCounter = value.resultSet.size,
            timeToResponse = value.timeToResponse
        )
        tryAndLogOrNull(log) {
            output.send(
                JasyncSearchBatchResults (
                    input = input,
                    jasyncSearchResultsList,
                    jasyncSearchMeters
                )
            )
        }
    }

    companion object {

        @JvmStatic
        private val log = logger()
    }
}
