package io.qalipsis.plugins.redis.lettuce.streams.consumer

/**
 * Qalipsis representation of a consumed Redis Streams record.
 *
 * @property offset of the record consumed by Qalipsis.
 * @property consumedTimestamp timestamp when the message was consumed by Qalipsis.
 * @property streamKey from where the message was consumed.
 * @property id of the message.
 * @property value of the record.
 *
 * @author Gabriel Moraes
 */
data class LettuceStreamsConsumedRecord(
    val offset: Long,
    val consumedTimestamp: Long,
    val id: String,
    val streamKey: String,
    val value: Map<String, String>,
) {
    internal constructor(offset: Long, id: String, stream: ByteArray, value: Map<String, String>) : this(
        offset = offset,
        consumedTimestamp = System.currentTimeMillis(),
        id = id,
        streamKey = stream.decodeToString(),
        value = value
    )
}
