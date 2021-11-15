package io.qalipsis.plugins.redis.lettuce.save


/**
 * Qalipsis representation of Redis record to be saved.
 *
 * @property key of the record.
 * @property value of record.
 * @property redisMethod the Redis method used to save the record.
 *
 * @author Gabriel Moraes
 */
interface LettuceSaveRecord<V>{
    val key: String
    val value: V
    var redisMethod : RedisLettuceSaveMethod

    fun getRecordBytesSize(): Int
}
