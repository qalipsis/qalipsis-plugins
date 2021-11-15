package io.qalipsis.plugins.redis.lettuce.streams.consumer

import io.micrometer.core.instrument.Counter

/**
 * Records the metrics for the Lettuce streams consumer.
 *
 * @property recordsCount counts the number of messages consumed.
 * @property valuesBytesReceived counts the number of bytes received.
 *
 * @author Gabriel Moraes
 */
data class LettuceStreamsConsumerMetrics(
    var recordsCount: Counter? = null,
    var valuesBytesReceived: Counter? = null,
)
