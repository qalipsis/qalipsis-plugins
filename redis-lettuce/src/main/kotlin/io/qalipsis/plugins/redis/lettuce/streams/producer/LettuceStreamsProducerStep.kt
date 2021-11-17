package io.qalipsis.plugins.redis.lettuce.streams.producer

import io.lettuce.core.api.StatefulConnection
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.cluster.api.StatefulRedisClusterConnection
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
import kotlinx.coroutines.withContext
import java.time.Duration
import kotlin.coroutines.CoroutineContext

/**
 * Implementation of a [io.qalipsis.api.steps.Step] able to produce a message into a Redis streams.
 *
 * @property id id of the step.
 * @property retryPolicy of the step.
 * @property connectionFactory supplier to create the connection with the Redis broker.
 * @property recordsFactory closure to generate the records to be published.
 * @property meterRegistry registry for the meters.
 * @property eventsLogger logger for the events.
 *
 * @author Gabriel Moraes
 */
internal class LettuceStreamsProducerStep<I>(
    id: StepId,
    retryPolicy: RetryPolicy?,
    private val ioCoroutineContext: CoroutineContext,
    private val connectionFactory: suspend () -> StatefulConnection<ByteArray, ByteArray>,
    private val recordsFactory: suspend (ctx: StepContext<*, *>, input: I) -> List<LettuceStreamsProduceRecord>,
    private val meterRegistry: MeterRegistry?,
    private val eventsLogger: EventsLogger?,
) : AbstractStep<I, LettuceStreamsProducerResult<I>>(id, retryPolicy) {

    private lateinit var connection: StatefulConnection<ByteArray, ByteArray>

    private lateinit var asyncCommand: RedisClusterAsyncCommands<ByteArray, ByteArray>

    private lateinit var monitoringCollector: LettuceMonitoringCollector

    private var sendingBytes: Counter? = null

    private var sentBytesMeter: Counter? = null

    private var sendingFailure: Counter? = null

    private val metersPrefix = "lettuce-streams"

    override suspend fun start(context: StepStartStopContext) {
        meterRegistry?.apply {
            val tags = context.toMetersTags()
            sendingBytes = counter("$metersPrefix-sending-bytes", tags)
            sentBytesMeter = counter("$metersPrefix-sent-bytes", tags)
            sendingFailure = counter("$metersPrefix-sending-failure", tags)
        }
        connection = connectionFactory()
        initialize()
    }

    private fun initialize() {
        asyncCommand = when (connection) {
            is StatefulRedisClusterConnection ->
                (connection as StatefulRedisClusterConnection<ByteArray, ByteArray>).async()
            is StatefulRedisConnection ->
                (connection as StatefulRedisConnection<ByteArray, ByteArray>).async()
            else -> throw IllegalStateException("Connection type is not implemented to perform redis commands")
        }
    }

    override suspend fun execute(context: StepContext<I, LettuceStreamsProducerResult<I>>) {
        val input = context.receive()
        val records = recordsFactory(context, input)

        monitoringCollector =
            LettuceMonitoringCollector(context, eventsLogger, sendingBytes, sentBytesMeter, sendingFailure, "streams")
        context.send(execute(monitoringCollector, input, records))
    }

    private suspend fun execute(
        monitoringCollector: LettuceMonitoringCollector,
        input: I,
        records: List<LettuceStreamsProduceRecord>
    ): LettuceStreamsProducerResult<I> {
        records.forEach { record ->
            withContext(ioCoroutineContext) {
                produce(record, monitoringCollector)
            }
        }

        return monitoringCollector.toResult(input)
    }

    private suspend fun produce(
        lettuceStreamsProduceRecord: LettuceStreamsProduceRecord,
        monitoringCollector: LettuceMonitoringCollector
    ) {
        val sentStart = System.nanoTime()
        val recordsByteSize =
            lettuceStreamsProduceRecord.value.map { it.key.toByteArray().size + it.value.toByteArray().size }.sum()
        monitoringCollector.recordSendingData(recordsByteSize)

        try {
            asyncCommand.xadd(
                lettuceStreamsProduceRecord.key.toByteArray(),
                lettuceStreamsProduceRecord.value.map { it.key.toByteArray() to it.value.toByteArray() }.toMap()
            ).asSuspended().get(DEFAULT_TIMEOUT)

            val now = System.nanoTime()
            monitoringCollector.recordSentDataSuccess(Duration.ofNanos(now - sentStart), recordsByteSize)
        } catch (e: Exception) {

            val now = System.nanoTime()
            monitoringCollector.recordSentDataFailure(Duration.ofNanos(now - sentStart), e)
        }
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
