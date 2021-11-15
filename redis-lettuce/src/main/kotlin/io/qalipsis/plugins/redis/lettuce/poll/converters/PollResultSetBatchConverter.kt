package io.qalipsis.plugins.redis.lettuce.poll.converters

import io.micrometer.core.instrument.Counter
import io.qalipsis.api.context.StepOutput
import io.qalipsis.api.lang.tryAndLogOrNull
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.steps.datasource.DatasourceObjectConverter
import io.qalipsis.plugins.redis.lettuce.RedisRecord
import io.qalipsis.plugins.redis.lettuce.poll.LettucePollMeters
import io.qalipsis.plugins.redis.lettuce.poll.LettucePollResult
import io.qalipsis.plugins.redis.lettuce.poll.PollRawResult
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Implementation of [DatasourceObjectConverter], to convert the whole result set into a unique record.
 *
 * @author Gabriel Moraes
 */
internal class PollResultSetBatchConverter(
    private val redisToJavaConverter: RedisToJavaConverter,
    private val recordsCounter: Counter?,
    private val recordsBytes: Counter?
) : DatasourceObjectConverter<PollRawResult<*>, LettucePollResult<Any?>> {

    /**
     * Converts the provided [value] from redis commands to a [RedisRecord] and send it in a [LettucePollResult] to the
     * [output] channel.
     *
     * This method uses [RedisToJavaConverter] to convert the specific redis return type to the specification for each command.
     * This method uses [RedisToJavaConverter] to get the size in bytes for each specific return type.
     *
     * When the [value] is provided by the ZSCAN, SSCAN or SCAN command it has a [List] type.
     * When the [value] is provided by the HSCAN command it has a [Map] type.
     *
     * @param offset to be sent in the [RedisRecord] offset.
     * @param value redis return specific type.
     * @param output channel to send the converted values in batch.
     */
    override suspend fun supply(
        offset: AtomicLong,
        value: PollRawResult<*>,
        output: StepOutput<LettucePollResult<Any?>>
    ) {
        recordsCounter?.increment(value.recordsCount.toDouble())
        val records = when (value.records) {
            is List<*> -> {
                value.records
            }
            is Map<*, *> -> {
                value.records.toList()
            }
            else -> {
                throw IllegalArgumentException("Not supported type: ${value::class}")
            }
        }

        tryAndLogOrNull(log) {
            val bytes = AtomicInteger(0)
            output.send(
                LettucePollResult(
                    records = records.map {
                        bytes.addAndGet(redisToJavaConverter.getBytesCount(it))
                        RedisRecord(
                            recordOffset = offset.getAndIncrement(),
                            recordTimestamp = System.currentTimeMillis(),
                            value = redisToJavaConverter.convert(it)
                        )

                    },
                    meters = LettucePollMeters(
                        timeToResult = value.timeToResult,
                        pollCount = value.pollCount,
                        valuesBytesReceived = bytes.get(),
                        recordsCount = value.recordsCount
                    )
                )
            )
            recordsBytes?.increment(bytes.toDouble())
        }
    }

    companion object {

        @JvmStatic
        private val log = logger()
    }

}
