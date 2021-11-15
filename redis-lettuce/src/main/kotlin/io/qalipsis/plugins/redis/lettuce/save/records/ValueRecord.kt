package io.qalipsis.plugins.redis.lettuce.save.records

import io.qalipsis.plugins.redis.lettuce.save.LettuceSaveRecord
import io.qalipsis.plugins.redis.lettuce.save.RedisLettuceSaveMethod

/**
 * Qalipsis representation of a Redis record to save using SET command.
 *
 * @property key of the record.
 * @property value of record.
 * @property redisMethod of the record to be saved.
 *
 * @author Gabriel Moraes
 */
data class ValueRecord internal constructor(
    override val key: String,
    override val value: String,
    override var redisMethod: RedisLettuceSaveMethod
) : LettuceSaveRecord<String> {

    constructor(key: String, value: String): this(key, value, RedisLettuceSaveMethod.SET)

    override fun getRecordBytesSize(): Int {
        return value.toByteArray().size + key.toByteArray().size
    }
}
