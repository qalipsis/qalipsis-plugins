package io.qalipsis.plugins.redis.lettuce.poll.scanners

import io.lettuce.core.RedisFuture
import io.lettuce.core.ScoredValue
import io.lettuce.core.ScoredValueScanCursor
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.cluster.api.StatefulRedisClusterConnection
import io.qalipsis.api.events.EventsLogger
import io.qalipsis.plugins.redis.lettuce.poll.LettuceScanner
import io.qalipsis.plugins.redis.lettuce.poll.RedisLettuceScanMethod

/**
 * Implementation of [LettuceScanner] for the ZSCAN type.
 *
 * @author Gabriel Moraes
 * @author Eric Jessé
 */
internal class LettuceZScanner(eventsLogger: EventsLogger?) :
    AbstractLettuceScanner<ScoredValueScanCursor<ByteArray>, MutableList<ScoredValue<ByteArray>>>(
        eventsLogger
    ) {

    override fun getType() = RedisLettuceScanMethod.ZSCAN

    override val clusterAction: (StatefulRedisClusterConnection<ByteArray, ByteArray>.(key: String, cursor: ScoredValueScanCursor<ByteArray>?) -> RedisFuture<ScoredValueScanCursor<ByteArray>>) =
        { key, cursor ->
            cursor?.let { c -> this.async().zscan(key.toByteArray(), c) } ?: this.async()
                .zscan(key.toByteArray())
        }

    override val singleNodeAction: (StatefulRedisConnection<ByteArray, ByteArray>.(key: String, cursor: ScoredValueScanCursor<ByteArray>?) -> RedisFuture<ScoredValueScanCursor<ByteArray>>) =
        { key, cursor ->
            cursor?.let { c -> this.async().zscan(key.toByteArray(), c) } ?: this.async()
                .zscan(key.toByteArray())
        }

    override fun collectValuesIntoResult(
        cursor: ScoredValueScanCursor<ByteArray>,
        collectedResult: MutableList<ScoredValue<ByteArray>>
    ): MutableList<ScoredValue<ByteArray>> {
        collectedResult.addAll(cursor.values)
        return collectedResult
    }

    override fun createResultCollector(): MutableList<ScoredValue<ByteArray>> {
        return mutableListOf()
    }

    override fun size(result: MutableList<ScoredValue<ByteArray>>): Int {
        return result.size
    }

}
