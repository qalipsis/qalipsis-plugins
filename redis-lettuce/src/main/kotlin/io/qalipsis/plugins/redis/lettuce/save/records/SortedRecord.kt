package io.qalipsis.plugins.redis.lettuce.save.records

import io.qalipsis.plugins.redis.lettuce.save.LettuceSaveRecord
import io.qalipsis.plugins.redis.lettuce.save.RedisLettuceSaveMethod

/**
 * Qalipsis representation of a Redis record to save using ZADD command.
 *
 * @property key of the record.
 * @property value of record.
 * @property redisMethod of the record to be saved.
 *
 * @author Gabriel Moraes
 */
data class SortedRecord internal constructor(
    override val key: String,
    override val value: Pair<Double, String>,
    override var redisMethod: RedisLettuceSaveMethod
) : LettuceSaveRecord<Pair<Double, String>> {

    constructor(key: String, value: Pair<Double, String>): this(key, value, RedisLettuceSaveMethod.ZADD)

    override fun getRecordBytesSize(): Int {
        return Double.SIZE_BYTES + value.second.toByteArray().size + key.toByteArray().size
    }
}
