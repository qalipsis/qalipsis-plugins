package io.qalipsis.plugins.mondodb.converters

import com.influxdb.query.FluxRecord
import io.qalipsis.api.context.StepOutput
import io.qalipsis.api.lang.tryAndLogOrNull
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.steps.datasource.DatasourceObjectConverter
import io.qalipsis.plugins.influxdb.poll.InfluxDbQueryResult
import java.util.concurrent.atomic.AtomicLong

/**
 * Implementation of [DatasourceObjectConverter], to send individual records to the output.
 *
 * @author Eric Jessé
 */
internal class InfluxDbDocumentPollSingleConverter(
) : DatasourceObjectConverter<InfluxDbQueryResult, FluxRecord> {

    override suspend fun supply(
        offset: AtomicLong,
        value: InfluxDbQueryResult,
        output: StepOutput<FluxRecord>
    ) {
        value.results.forEach {
            tryAndLogOrNull(log) {
                output.send(it)
            }
        }
    }

    companion object {
        @JvmStatic
        private val log = logger()
    }
}
