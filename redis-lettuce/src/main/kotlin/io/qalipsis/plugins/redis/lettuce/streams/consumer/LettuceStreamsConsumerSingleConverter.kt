package io.qalipsis.plugins.redis.lettuce.streams.consumer

import io.lettuce.core.StreamMessage
import io.qalipsis.api.context.StepOutput
import io.qalipsis.api.lang.tryAndLogOrNull
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.steps.datasource.DatasourceObjectConverter
import java.util.concurrent.atomic.AtomicLong

/**
 * Implementation of [DatasourceObjectConverter], that reads a message of native Redis Streams records and forwards
 * each of them converted as [LettuceStreamsConsumedRecord].
 *
 * @author Gabriel Moraes
 */
internal class LettuceStreamsConsumerSingleConverter(
    private val metrics: LettuceStreamsConsumerMetrics,
) : DatasourceObjectConverter<List<StreamMessage<ByteArray, ByteArray>>, LettuceStreamsConsumedRecord> {

    override suspend fun supply(
        offset: AtomicLong, value: List<StreamMessage<ByteArray, ByteArray>>,
        output: StepOutput<LettuceStreamsConsumedRecord>
    ) {
        value.forEach { record ->

            metrics.valuesBytesReceived?.increment(record.body.values.sumOf { it.size }.toDouble())
            metrics.recordsCount?.increment()

            tryAndLogOrNull(log) {
                output.send(
                    LettuceStreamsConsumedRecord(
                        offset.getAndIncrement(),
                        record.id,
                        record.stream,
                        record.body.map { it.key.decodeToString() to it.value.decodeToString() }.toMap()
                    )
                )
            }
        }
    }

    companion object {

        @JvmStatic
        private val log = logger()
    }
}
