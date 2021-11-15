package io.qalipsis.plugins.redis.lettuce.poll

import java.time.Duration

/**
 * Contains the raw results of a poll operation.
 *
 * @property records values returned by the poll statement
 * @property recordsCount number of items in [records]
 * @property timeToResult time to proceed with a single poll statement
 * @property pollCount number of poll iterations perform until the cursor is finished
 *
 * @author Eric Jessé
 */
internal data class PollRawResult<T>(
    val records: T,
    val recordsCount: Int,
    val timeToResult: Duration,
    val pollCount: Int
)
