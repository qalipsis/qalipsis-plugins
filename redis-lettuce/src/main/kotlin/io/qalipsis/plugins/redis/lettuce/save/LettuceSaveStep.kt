package io.qalipsis.plugins.redis.lettuce.save

import io.lettuce.core.RedisFuture
import io.lettuce.core.api.StatefulConnection
import io.lettuce.core.cluster.api.async.RedisClusterAsyncCommands
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.context.StepId
import io.qalipsis.api.context.StepStartStopContext
import io.qalipsis.api.events.EventsLogger
import io.qalipsis.api.lang.tryAndLog
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.retry.RetryPolicy
import io.qalipsis.api.steps.AbstractStep
import io.qalipsis.api.sync.asSuspended
import io.qalipsis.plugins.redis.lettuce.LettuceMonitoringCollector
import io.qalipsis.plugins.redis.lettuce.configuration.RedisCommandsFactory
import io.qalipsis.plugins.redis.lettuce.save.records.HashRecord
import io.qalipsis.plugins.redis.lettuce.save.records.SetRecord
import io.qalipsis.plugins.redis.lettuce.save.records.SortedRecord
import io.qalipsis.plugins.redis.lettuce.save.records.ValueRecord
import kotlinx.coroutines.withContext
import java.time.Duration
import kotlin.coroutines.CoroutineContext

/**
 * Implementation of a [io.qalipsis.api.steps.Step] able to save a batch of records into a Redis database.
 *
 * @property id of the step.
 * @property retryPolicy of the step.
 * @property connectionFactory supplier to create the connection with the Redis database.
 * @property recordsFactory closure to generate the records to be saved.
 * @property meterRegistry registry for the meters.
 * @property eventsLogger logger for the events.
 *
 * @author Gabriel Moraes
 */
internal class LettuceSaveStep<I>(
    id: StepId,
    retryPolicy: RetryPolicy?,
    private val ioCoroutineContext: CoroutineContext,
    private val connectionFactory: suspend () -> StatefulConnection<ByteArray, ByteArray>,
    private val recordsFactory: suspend (ctx: StepContext<*, *>, input: I) -> List<LettuceSaveRecord<*>>,
    private val meterRegistry: MeterRegistry?,
    private val eventsLogger: EventsLogger?,
) : AbstractStep<I, LettuceSaveResult<I>>(id, retryPolicy) {

    private lateinit var connection: StatefulConnection<ByteArray, ByteArray>

    private lateinit var redisAsyncCommands: RedisClusterAsyncCommands<ByteArray, ByteArray>

    private lateinit var monitoringCollector: LettuceMonitoringCollector

    private var sendingBytes: Counter? = null

    private var sentBytesMeter: Counter? = null

    private var sendingFailure: Counter? = null

    private val metersPrefix = "lettuce-save"

    override suspend fun start(context: StepStartStopContext) {
        meterRegistry?.apply {
            val tags = context.toMetersTags()
            sendingBytes = counter("$metersPrefix-sending-bytes", tags)
            sentBytesMeter = counter("$metersPrefix-sent-bytes", tags)
            sendingFailure = counter("$metersPrefix-sending-failure", tags)
        }
        connection = connectionFactory()
        redisAsyncCommands = RedisCommandsFactory.getAsyncCommand(connection)
    }

    override suspend fun execute(context: StepContext<I, LettuceSaveResult<I>>) {
        val input = context.receive()

        val records = recordsFactory(context, input)

        monitoringCollector =
            LettuceMonitoringCollector(context, eventsLogger, sendingBytes, sentBytesMeter, sendingFailure, "save")

        context.send(execute(monitoringCollector, input, records))
    }

    private suspend fun execute(
        monitoringCollector: LettuceMonitoringCollector,
        input: I,
        records: List<LettuceSaveRecord<*>>
    ): LettuceSaveResult<I> {
        records.forEach { record ->
            withContext(ioCoroutineContext) {
                save(record, monitoringCollector)
            }
        }
        return monitoringCollector.toSaveResult(input)
    }

    private suspend fun save(lettuceSaveRecord: LettuceSaveRecord<*>, monitoringCollector: LettuceMonitoringCollector) {
        val sentStart = System.nanoTime()
        val recordsByteSize = lettuceSaveRecord.getRecordBytesSize()
        monitoringCollector.recordSendingData(recordsByteSize)

        try {
            when (lettuceSaveRecord) {
                is HashRecord -> saveHashRecord(lettuceSaveRecord)
                is SortedRecord -> saveSortedRecord(lettuceSaveRecord)
                is SetRecord -> saveSetRecord(lettuceSaveRecord)
                is ValueRecord -> saveValueRecord(lettuceSaveRecord)
                else -> throw IllegalStateException("Not supported Lettuce save record type")
            }.asSuspended().get(DEFAULT_TIMEOUT)

            val now = System.nanoTime()
            monitoringCollector.recordSentDataSuccess(Duration.ofNanos(now - sentStart), recordsByteSize)
        } catch (e: Exception) {

            val now = System.nanoTime()
            monitoringCollector.recordSentDataFailure(Duration.ofNanos(now - sentStart), e)
        }
    }

    private fun saveValueRecord(lettuceSaveRecord: ValueRecord): RedisFuture<out Any> {
        return redisAsyncCommands.set(lettuceSaveRecord.key.toByteArray(), lettuceSaveRecord.value.toByteArray())
    }

    private fun saveSetRecord(lettuceSaveRecord: SetRecord): RedisFuture<out Any> {
        return redisAsyncCommands.sadd(lettuceSaveRecord.key.toByteArray(), lettuceSaveRecord.value.toByteArray())
    }

    private fun saveSortedRecord(lettuceSaveRecord: SortedRecord): RedisFuture<out Any> {
        return redisAsyncCommands.zadd(
            lettuceSaveRecord.key.toByteArray(),
            lettuceSaveRecord.value.first,
            lettuceSaveRecord.value.second.toByteArray()
        )
    }

    private fun saveHashRecord(lettuceSaveRecord: HashRecord): RedisFuture<out Any> {
        val byteArrayMap = lettuceSaveRecord.value.map { it.key.toByteArray() to it.value.toByteArray() }.toMap()
        return redisAsyncCommands.hset(lettuceSaveRecord.key.toByteArray(), byteArrayMap)
    }

    override suspend fun stop(context: StepStartStopContext) {
        meterRegistry?.apply {
            remove(sendingBytes!!)
            remove(sentBytesMeter!!)
            remove(sendingFailure!!)
            sendingBytes = null
            sentBytesMeter = null
            sendingFailure = null
        }
        tryAndLog(log) { connection.closeAsync()?.asSuspended() }
        tryAndLog(log) { connection.resources.shutdown() }
    }

    companion object {

        private val DEFAULT_TIMEOUT = Duration.ofSeconds(10)

        @JvmStatic
        private val log = logger()
    }
}
