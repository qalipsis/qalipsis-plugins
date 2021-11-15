package io.qalipsis.plugins.redis.lettuce.streams.consumer

/**
 * Qalispsis representation of a Lettuce Redis Consumer result.
 *
 * @property records list of Redis records.
 * @property meters metrics of the poll step.
 *
 * @author Svetlana Paliashchuk
 */
data class LettuceStreamsConsumerResult(
        val records: List<LettuceStreamsConsumedRecord>,
        val meters: LettuceStreamsConsumerMetrics
) : Iterable<LettuceStreamsConsumedRecord> {
    override fun iterator(): Iterator<LettuceStreamsConsumedRecord> {
        return records.iterator()
    }
}