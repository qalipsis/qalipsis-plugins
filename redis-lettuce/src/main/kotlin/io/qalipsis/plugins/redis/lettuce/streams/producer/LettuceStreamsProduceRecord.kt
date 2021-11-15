package io.qalipsis.plugins.redis.lettuce.streams.producer

/**
 * Qalipsis representation of Redis Streams record to produce.
 *
 * @property key of the streams to produce.
 * @property value of the streams produced.
 *
 * @author Gabriel Moraes
 */
data class LettuceStreamsProduceRecord(
    val key: String,
    val value: Map<String, String>,
)
