package io.qalipsis.plugins.redis.lettuce.streams.consumer

import io.lettuce.core.StreamMessage
import io.qalipsis.api.context.StepOutput
import io.qalipsis.api.lang.tryAndLogOrNull
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.steps.datasource.DatasourceObjectConverter
import java.util.concurrent.atomic.AtomicLong

/**
 * Implementation of [DatasourceObjectConverter], that reads a message of native Redis Streams records and forwards
 * it converted as a list of [LettuceStreamsConsumedRecord].
 *
 * @author Gabriel Moraes
 */
internal class LettuceStreamsConsumerBatchConverter(
    private val metrics: LettuceStreamsConsumerMetrics,
) : DatasourceObjectConverter<List<StreamMessage<ByteArray, ByteArray>>, LettuceStreamsConsumerResult> {

    override suspend fun supply(
        offset: AtomicLong, value: List<StreamMessage<ByteArray, ByteArray>>,
        output: StepOutput<LettuceStreamsConsumerResult>
    ) {

        tryAndLogOrNull(log) {
            output.send(LettuceStreamsConsumerResult(
                records = value.map { record ->

                    metrics.valuesBytesReceived?.increment(record.body.values.sumOf { it.size }.toDouble())
                    metrics.recordsCount?.increment()

                    LettuceStreamsConsumedRecord(
                        offset.getAndIncrement(),
                        record.id,
                        record.stream,
                        record.body.map { it.key.decodeToString() to it.value.decodeToString() }.toMap()
                    )
                },
                    meters = metrics
            )
            )
        }
    }

    companion object {

        @JvmStatic
        private val log = logger()
    }
}
