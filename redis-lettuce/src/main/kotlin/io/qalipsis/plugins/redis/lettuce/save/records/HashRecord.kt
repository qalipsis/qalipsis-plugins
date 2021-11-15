package io.qalipsis.plugins.redis.lettuce.save.records

import io.qalipsis.plugins.redis.lettuce.save.LettuceSaveRecord
import io.qalipsis.plugins.redis.lettuce.save.RedisLettuceSaveMethod

/**
 * Qalipsis representation of a Redis record to save using HSET command.
 *
 * @property key of the record.
 * @property value of record.
 * @property redisMethod of the record to be saved.
 *
 * @author Gabriel Moraes
 */
data class HashRecord internal constructor(
    override val key: String,
    override val value: Map<String, String>,
    override var redisMethod: RedisLettuceSaveMethod
) : LettuceSaveRecord<Map<String, String>> {

    constructor(key: String, value: Map<String, String>): this(key, value, RedisLettuceSaveMethod.HSET)

    override fun getRecordBytesSize(): Int {
        return value.map { it.key.toByteArray().size + it.value.toByteArray().size }.sum() + key.toByteArray().size
    }
}
