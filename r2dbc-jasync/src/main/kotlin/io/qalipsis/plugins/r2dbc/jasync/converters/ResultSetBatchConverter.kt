package io.qalipsis.plugins.r2dbc.jasync.converters

import com.github.jasync.sql.db.ResultSet
import io.micrometer.core.instrument.Counter
import io.qalipsis.api.context.StepOutput
import io.qalipsis.api.lang.tryAndLogOrNull
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.steps.datasource.DatasourceObjectConverter
import io.qalipsis.api.steps.datasource.DatasourceRecord
import kotlinx.coroutines.channels.SendChannel
import java.util.concurrent.atomic.AtomicLong

/**
 * Implementation of [DatasourceObjectConverter], to convert the whole result set into a unique record.
 *
 * @author Eric Jessé
 */
internal class ResultSetBatchConverter(
    private val resultValuesConverter: ResultValuesConverter,
    private val recordsCounter: Counter?
) : DatasourceObjectConverter<ResultSet, List<DatasourceRecord<Map<String, Any?>>>> {

    override suspend fun supply(
        offset: AtomicLong,
        value: ResultSet,
        output: StepOutput<List<DatasourceRecord<Map<String, Any?>>>>
    ) {
        recordsCounter?.increment(value.size.toDouble())

        tryAndLogOrNull(log) {
            output.send(
                value.map { row ->
                    DatasourceRecord(
                        offset.getAndIncrement(),
                        value.columnNames().map { column ->
                            column to resultValuesConverter.process(row[column])
                        }.toMap()
                    )

                }
            )
        }
    }

    companion object {

        @JvmStatic
        private val log = logger()
    }
}
