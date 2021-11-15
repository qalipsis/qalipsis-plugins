package io.qalipsis.plugins.redis.lettuce


/**
 * Qalipsis representation of a Redis record.
 *
 *
 * @property recordOffset offset of the processed record by Qalipsis.
 * @property recordTimestamp timestamp when the record was processed by Qalipsis.
 * @property value of the record after deserialization.
 *
 * @author Gabriel Moraes
 */
data class RedisRecord<V>(
    val recordOffset: Long,
    val recordTimestamp: Long,
    val value: V
)