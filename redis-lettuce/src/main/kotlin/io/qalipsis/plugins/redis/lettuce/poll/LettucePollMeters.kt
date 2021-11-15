package io.qalipsis.plugins.redis.lettuce.poll

import java.time.Duration

/**
 * Meters of a unique poll call using Lettuce to Redis.
 *
 * @property timeToResult time to proceed with a single poll statement
 * @property pollCount number of poll iterations perform until the cursor is finished
 * @property recordsCount number of items in the result
 * @property valuesBytesReceived number of bytes contains in all the items of the result
 *
 * @author Svetlana Paliashchuk
 */
data class LettucePollMeters(
    val timeToResult: Duration,
    val pollCount: Int,
    val recordsCount: Int,
    val valuesBytesReceived: Int,
)
