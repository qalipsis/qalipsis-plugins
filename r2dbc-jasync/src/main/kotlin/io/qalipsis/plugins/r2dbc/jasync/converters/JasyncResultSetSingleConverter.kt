package io.qalipsis.plugins.r2dbc.jasync.converters

import com.github.jasync.sql.db.ResultSet
import io.micrometer.core.instrument.Counter
import io.qalipsis.api.context.StepOutput
import io.qalipsis.api.lang.tryAndLogOrNull
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.steps.datasource.DatasourceRecord
import kotlinx.coroutines.channels.SendChannel
import java.util.concurrent.atomic.AtomicLong

/**
 * Implementation of [JasyncResultSetConverter], to convert the whole result set into single records.
 *
 * @author Fiodar Hmyza
 */
internal class JasyncResultSetSingleConverter<I>(
    private val resultValuesConverter: ResultValuesConverter,
    private val recordsCounter: Counter?
) : JasyncResultSetConverter<ResultSet, Pair<I, DatasourceRecord<Map<String, Any?>>>, I> {

    override suspend fun supply(
        offset: AtomicLong,
        value: ResultSet,
        input: I,
        output: StepOutput<Pair<I, DatasourceRecord<Map<String, Any?>>>>
    ) {
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
                output.send(input to it)
            }
        }
    }

    companion object {

        @JvmStatic
        private val log = logger()
    }
}
