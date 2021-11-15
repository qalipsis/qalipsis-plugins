package io.qalipsis.plugins.redis.lettuce.poll

import io.lettuce.core.api.StatefulConnection
import io.qalipsis.api.context.StepStartStopContext
import io.qalipsis.api.lang.tryAndLog
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.steps.datasource.DatasourceIterativeReader
import io.qalipsis.api.sync.asSuspended
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.time.Duration

/**
 * Database reader based upon [Lettuce Driver](https://github.com/lettuce-io/lettuce-core) for Redis.
 *
 * @property connectionFactory supplier factory for the DB connection.
 * @property pollDelay duration between the end of a poll and the start of the next one.
 * @property pattern used to execute commands.
 * @property lettuceScanner executor for each specific redis method of [RedisLettuceScanMethod].
 * @property resultsChannelFactory factory to create the channel containing the received results sets.
 * @property running running state of the reader.
 * @property pollingJob instance of the background job polling data from the database.
 *
 * @author Gabriel Moraes
 */
internal class LettuceIterativeReader(
    private val ioCoroutineScope: CoroutineScope,
    private val connectionFactory: suspend () -> StatefulConnection<ByteArray, ByteArray>,
    private val pollDelay: Duration,
    private val pattern: String,
    private val lettuceScanner: LettuceScanner,
    private val resultsChannelFactory: () -> Channel<PollRawResult<*>>
) : DatasourceIterativeReader<PollRawResult<*>> {

    private lateinit var connection: StatefulConnection<ByteArray, ByteArray>

    private var running = false

    private lateinit var pollingJob: Job

    private lateinit var resultsChannel: Channel<PollRawResult<*>>

    override fun start(context: StepStartStopContext) {
        init()
        running = true
        pollingJob = ioCoroutineScope.launch {
            try {
                while (running) {
                    poll(connection, context)
                    if (running) {
                        delay(pollDelay.toMillis())
                    }
                }
            } finally {
                connection.closeAsync()?.asSuspended()?.get(CONNECTION_TIMEOUT)
                resultsChannel.cancel()
            }
        }
    }

    private fun init() {
        resultsChannel = resultsChannelFactory()
        runBlocking {
            connection = connectionFactory()
        }
    }

    /**
     * Polls next available batch of records from the database.
     *
     * @param connection the active connection
     */
    private suspend fun poll(
        connection: StatefulConnection<ByteArray, ByteArray>,
        context: StepStartStopContext
    ) {
        try {
            lettuceScanner.execute(connection, pattern, resultsChannel, context.toEventTags())
        } catch (e: Exception) {
            // Logs the error but allow next poll.
            log.error(e) { e.message }
        }
    }

    override fun stop(context: StepStartStopContext) {
        running = false
        runCatching {
            runBlocking {
                pollingJob.cancelAndJoin()
            }
        }
        resultsChannel.cancel()
        tryAndLog(log) { connection.closeAsync()?.asSuspended() }
        tryAndLog(log) { connection.resources.shutdown() }
    }

    override suspend fun hasNext(): Boolean {
        return running
    }

    override suspend fun next(): PollRawResult<*> {
        return resultsChannel.receive()
    }

    companion object {

        @JvmStatic
        private val log = logger()

        private val CONNECTION_TIMEOUT = Duration.ofSeconds(10)

    }
}
