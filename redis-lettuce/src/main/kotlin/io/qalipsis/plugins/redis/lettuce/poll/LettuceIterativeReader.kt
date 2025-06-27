/*
 * QALIPSIS
 * Copyright (C) 2025 AERIS IT Solutions GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

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
        resultsChannel = resultsChannelFactory()
        running = true
        pollingJob = ioCoroutineScope.launch {
            connection = connectionFactory()
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
