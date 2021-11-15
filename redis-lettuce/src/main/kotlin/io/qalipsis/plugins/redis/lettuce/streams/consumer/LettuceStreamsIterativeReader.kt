package io.qalipsis.plugins.redis.lettuce.streams.consumer

import io.lettuce.core.Consumer
import io.lettuce.core.StreamMessage
import io.lettuce.core.XGroupCreateArgs
import io.lettuce.core.XReadArgs
import io.lettuce.core.api.StatefulConnection
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.cluster.api.StatefulRedisClusterConnection
import io.lettuce.core.cluster.api.async.RedisClusterAsyncCommands
import io.qalipsis.api.context.StepStartStopContext
import io.qalipsis.api.events.EventsLogger
import io.qalipsis.api.lang.tryAndLog
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.steps.datasource.DatasourceIterativeReader
import io.qalipsis.api.sync.asSuspended
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.time.Duration
import java.util.UUID
import java.util.concurrent.CompletionStage
import java.util.concurrent.TimeUnit

/**
 * Implementation of [DatasourceIterativeReader] to consumer records from Redis streams.
 *
 * This implementation supports multiple consumers using the property [concurrency].
 *
 * @property connectionFactory supplier to create the connection with the Redis broker.
 * @property groupName name of the consumer group.
 * @property concurrency quantity of concurrent consumers.
 * @property streamKey key of the stream to consume from.
 * @property offset of the consumer group.
 *
 * @author Gabriel Moraes
 */
internal class LettuceStreamsIterativeReader(
    private val ioCoroutineScope: CoroutineScope,
    private val ioCoroutineDispatcher: CoroutineDispatcher,
    private val connectionFactory: suspend () -> CompletionStage<out StatefulConnection<ByteArray, ByteArray>>,
    private val groupName: String,
    private val concurrency: Int,
    private val streamKey: String,
    private val offset: String,
    private val eventsLogger: EventsLogger
) : DatasourceIterativeReader<List<StreamMessage<ByteArray, ByteArray>>> {

    private lateinit var resultChannel: Channel<List<StreamMessage<ByteArray, ByteArray>>>

    private lateinit var connection: StatefulConnection<ByteArray, ByteArray>

    private var pollingJobs = mutableListOf<Job>()

    private var running = false

    private lateinit var redisAsyncCommand: RedisClusterAsyncCommands<ByteArray, ByteArray>

    override fun start(context: StepStartStopContext) {
        running = true
        runBlocking(ioCoroutineDispatcher) {
            init()
            createConsumerGroup()
        }
        suspendedStart(context)
    }

    private suspend fun init() {
        connection = connectionFactory().asSuspended().get(DEFAULT_TIMEOUT_IN_DURATION)
        redisAsyncCommand = when (connection) {
            is StatefulRedisClusterConnection -> (connection as StatefulRedisClusterConnection<ByteArray, ByteArray>).async()
            is StatefulRedisConnection -> (connection as StatefulRedisConnection<ByteArray, ByteArray>).async()
            else -> throw IllegalStateException("Connection type is not implemented to perform redis commands")
        }

        resultChannel = Channel(Channel.UNLIMITED)
    }

    private suspend fun createConsumerGroup() {
        val groupExists = kotlin.runCatching {
            redisAsyncCommand.xinfoGroups(streamKey.toByteArray())
                .asSuspended().get(DEFAULT_TIMEOUT_IN_DURATION).map {
                    (it as List<Any>).map { field ->
                        if (field is ByteArray) {
                            field.toString(Charsets.UTF_8)
                        } else {
                            field
                        }
                    }
                }.any { infos ->
                    val nameFieldIndex = infos.indexOf("name")
                    infos[nameFieldIndex + 1] == groupName
                }
        }.getOrDefault(false)

        if (!groupExists) {
            log.debug { "The group $groupName already does not exist yet and has to be created" }
            redisAsyncCommand.xgroupCreate(
                XReadArgs.StreamOffset.from(streamKey.toByteArray(), offset),
                groupName.toByteArray(),
                XGroupCreateArgs.Builder.mkstream(true)
            ).asSuspended().get(DEFAULT_TIMEOUT_IN_DURATION)
        } else {
            log.debug { "The group $groupName already exists" }
        }
    }

    private fun suspendedStart(context: StepStartStopContext) {
        repeat(concurrency) { pollingJobs.add(ioCoroutineScope.launch { readMessages(context) }) }
    }

    private suspend fun readMessages(context: StepStartStopContext) {

        val consumerGroup = Consumer.from(
            groupName.toByteArray(), UUID.randomUUID().toString().toByteArray()
        )
        val streamOffset = XReadArgs.StreamOffset.lastConsumed(streamKey.toByteArray())
        while (running) {
            val requestStart = System.nanoTime()
            val messages = redisAsyncCommand.xreadgroup(
                consumerGroup, streamOffset
            ).asSuspended().get(DEFAULT_TIMEOUT_IN_DURATION)
            eventsLogger.info(
                "lettuce.streams.consumer.response-time",
                System.nanoTime() - requestStart,
                tags = context.toEventTags()
            )
            eventsLogger.info("lettuce.streams.consumer.total-messages", messages.size, tags = context.toEventTags())

            if (messages.isNotEmpty()) {
                acknowledgeMessages(messages)
                resultChannel.trySend(messages).getOrThrow()
            }
        }
    }

    private suspend fun acknowledgeMessages(messages: MutableList<StreamMessage<ByteArray, ByteArray>>) {
        redisAsyncCommand.xack(
            streamKey.toByteArray(),
            groupName.toByteArray(),
            *messages.map { it.id }.toTypedArray()
        ).asSuspended().get(DEFAULT_TIMEOUT_IN_DURATION)
    }


    override fun stop(context: StepStartStopContext) {
        running = false

        runCatching {
            runBlocking(ioCoroutineDispatcher) {
                pollingJobs.forEach { it.cancelAndJoin() }
            }
        }
        pollingJobs.clear()
        tryAndLog(log) { connection.closeAsync().get(DEFAULT_TIMEOUT_IN_DURATION.toMillis(), TimeUnit.MILLISECONDS) }
        tryAndLog(log) { connection.resources.shutdown() }

        resultChannel.cancel()
        log.info { "Lettuce Redis Streams consumer was stopped" }
    }

    override suspend fun hasNext(): Boolean {
        return running
    }

    override suspend fun next(): List<StreamMessage<ByteArray, ByteArray>> {
        return resultChannel.receive()
    }

    companion object {

        private val DEFAULT_TIMEOUT_IN_DURATION = Duration.ofSeconds(5)

        @JvmStatic
        private val log = logger()
    }

}
