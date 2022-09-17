/*
 * Copyright 2022 AERIS IT Solutions GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package io.qalipsis.plugins.redis.lettuce.poll.scanners

import io.lettuce.core.RedisFuture
import io.lettuce.core.ScanCursor
import io.lettuce.core.api.StatefulConnection
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.cluster.api.StatefulRedisClusterConnection
import io.qalipsis.api.events.EventsLogger
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.sync.asSuspended
import io.qalipsis.plugins.redis.lettuce.poll.LettuceScanner
import io.qalipsis.plugins.redis.lettuce.poll.PollRawResult
import kotlinx.coroutines.channels.Channel
import java.time.Duration

/**
 * Abstract implementation of [LettuceScanner] to share common methods among the implementations.
 *
 * @author Gabriel Moraes
 * @author Eric Jessé
 */
internal abstract class AbstractLettuceScanner<CURSOR : ScanCursor, RESULT : Any>(private val eventsLogger: EventsLogger?) :
    LettuceScanner {

    /**
     * Action to perform for each poll statement, when the connection is a [StatefulRedisClusterConnection].
     */
    abstract val clusterAction: (StatefulRedisClusterConnection<ByteArray, ByteArray>.(keyOrPattern: String, cursor: CURSOR?) -> RedisFuture<CURSOR>)

    /**
     * Action to perform for each poll statement, when the connection is a [StatefulRedisConnection].
     */
    abstract val singleNodeAction: (StatefulRedisConnection<ByteArray, ByteArray>.(keyOrPattern: String, cursor: CURSOR?) -> RedisFuture<CURSOR>)

    /**
     * Returns a [RESULT] containing the previous [collectedResult] and the newly received values from the [cursor].
     */
    abstract fun collectValuesIntoResult(cursor: CURSOR, collectedResult: RESULT): RESULT

    /**
     * Creates a new instance of [RESULT] that can be used to collect the received values.
     */
    abstract fun createResultCollector(): RESULT

    /**
     * Returns the number of items in [RESULT].
     */
    abstract fun size(result: RESULT): Int

    /**
     * Delegates the call to the specific implementation of each redis command to execute in a cluster
     * connection or in a single node.
     */
    override suspend fun execute(
        connection: StatefulConnection<ByteArray, ByteArray>, pattern: String,
        resultsChannel: Channel<PollRawResult<*>>, contextEventTags: Map<String, String>
    ) {
        when (connection) {
            is StatefulRedisClusterConnection -> execute(
                contextEventTags,
                pattern
            ) { keyOrPattern, cursor -> connection.clusterAction(keyOrPattern, cursor) }
            is StatefulRedisConnection -> execute(
                contextEventTags,
                pattern
            ) { keyOrPattern, cursor -> connection.singleNodeAction(keyOrPattern, cursor) }
            else -> throw IllegalStateException("Connection type is not implemented to perform redis commands")
        }?.takeIf { it.recordsCount > 0 }?.let { resultsChannel.send(it) }
    }

    private suspend fun execute(
        contextEventTags: Map<String, String>,
        keyOrPattern: String,
        command: (String, CURSOR?) -> RedisFuture<CURSOR>
    ): PollRawResult<RESULT>? {

        var pollCount = 0
        var cursor: CURSOR? = null
        var result = createResultCollector()
        val overallStart = System.nanoTime()

        return try {
            eventsLogger?.trace("lettuce.poll.polling", tags = contextEventTags)
            while (cursor?.isFinished != true) {
                cursor = command(keyOrPattern, cursor).asSuspended().get(DEFAULT_TIMEOUT)
                pollCount++
                result = collectValuesIntoResult(cursor, result)
            }
            val overAllDuration = Duration.ofNanos(System.nanoTime() - overallStart)
            eventsLogger?.info("lettuce.poll.response", arrayOf(overAllDuration, size(result)), tags = contextEventTags)

            PollRawResult(
                result,
                size(result),
                overAllDuration,
                pollCount
            )
        } catch (e: Exception) {
            val overAllDuration = Duration.ofNanos(System.nanoTime() - overallStart)
            eventsLogger?.warn("lettuce.poll.failure", arrayOf(overAllDuration, e), tags = contextEventTags)
            log.error(e) { "An error occurred while polling: ${e.message}" }
            null
        }
    }

    companion object {

        /**
         * Timeout used by default for all [AbstractLettuceScanner] implementations when sending redis
         * commands.
         */
        internal val DEFAULT_TIMEOUT = Duration.ofSeconds(30)

        @JvmStatic
        private val log = logger()
    }
}
