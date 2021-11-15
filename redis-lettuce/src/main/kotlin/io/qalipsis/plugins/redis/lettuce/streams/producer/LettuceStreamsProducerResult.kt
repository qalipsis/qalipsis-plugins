package io.qalipsis.plugins.redis.lettuce.streams.producer

import io.qalipsis.plugins.redis.lettuce.Meters

/**
 * Qalispsis representation of a Lettuce Redis Producer result.
 *
 * @property input from the previous steps output.
 * @property sendingFailures failures when sending redis streams commands.
 * @property meters for the step execution.
 *
 * @author Gabriel Moraes
 */
data class LettuceStreamsProducerResult<I>(
    val input: I,
    val sendingFailures: List<Throwable>?,
    val meters: Meters
)
