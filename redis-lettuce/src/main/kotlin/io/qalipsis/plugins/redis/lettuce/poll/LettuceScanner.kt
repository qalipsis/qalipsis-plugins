package io.qalipsis.plugins.redis.lettuce.poll

import io.lettuce.core.api.StatefulConnection
import kotlinx.coroutines.channels.Channel


/**
 * Specification to execute a specific scan command from Redis for each type of [RedisLettuceScanMethod].
 *
 * @author Gabriel Moraes
 */
internal interface LettuceScanner {

    /**
     * Returns the [RedisLettuceScanMethod] of the implementation.
     */
    fun getType(): RedisLettuceScanMethod

    /**
     * Executes a scan command based on the [RedisLettuceScanMethod] of the implementation.
     *
     * @param connection used to perform the command on Redis.
     * @param pattern used to perform the command.
     */
    suspend fun execute(
        connection: StatefulConnection<ByteArray, ByteArray>, pattern: String,
        resultsChannel: Channel<PollRawResult<*>>, contextEventTags: Map<String, String>
    )
}
