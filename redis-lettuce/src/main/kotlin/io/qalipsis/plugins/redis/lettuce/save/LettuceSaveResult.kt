package io.qalipsis.plugins.redis.lettuce.save

import io.qalipsis.plugins.redis.lettuce.Meters

/**
 * Qalispsis representation of a Lettuce Redis Save result.
 *
 * @property input from the previous steps output.
 * @property sendingFailures failures when sending redis save commands.
 * @property meters for the step execution.
 *
 * @author Gabriel Moraes
 */
data class LettuceSaveResult<I>(
    val input: I,
    val sendingFailures: List<Throwable>?,
    val meters: Meters
)
