package io.qalipsis.plugins.redis.lettuce.poll

import io.qalipsis.plugins.redis.lettuce.RedisRecord

/**
 * Wrapper for the result of poll in Redis.
 *
 * @author Svetlana Paliashchuk
 *
 * @property records list of Redis records.
 * @property meters metrics of the poll step.
 *
 */
data class LettucePollResult<V>(
    val records: List<RedisRecord<V>>,
    val meters: LettucePollMeters
) : Iterable<RedisRecord<V>> {

    override fun iterator(): Iterator<RedisRecord<V>> {
        return records.iterator()
    }
}
