package io.qalipsis.plugins.redis.lettuce.poll.scanners

import io.lettuce.core.RedisFuture
import io.lettuce.core.ValueScanCursor
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.cluster.api.StatefulRedisClusterConnection
import io.qalipsis.api.events.EventsLogger
import io.qalipsis.plugins.redis.lettuce.poll.LettuceScanner
import io.qalipsis.plugins.redis.lettuce.poll.RedisLettuceScanMethod

/**
 * Implementation of [LettuceScanner] for the SSCAN type.
 *
 * @author Gabriel Moraes
 * @author Eric Jessé
 */
internal class LettuceSScanner(eventsLogger: EventsLogger?) :
    AbstractLettuceScanner<ValueScanCursor<ByteArray>, MutableList<ByteArray>>(eventsLogger) {

    override fun getType() = RedisLettuceScanMethod.SSCAN

    override val clusterAction: (StatefulRedisClusterConnection<ByteArray, ByteArray>.(key: String, cursor: ValueScanCursor<ByteArray>?) -> RedisFuture<ValueScanCursor<ByteArray>>) =
        { key, cursor ->
            cursor?.let { c -> this.async().sscan(key.toByteArray(), c) } ?: this.async()
                .sscan(key.toByteArray())
        }

    override val singleNodeAction: (StatefulRedisConnection<ByteArray, ByteArray>.(key: String, cursor: ValueScanCursor<ByteArray>?) -> RedisFuture<ValueScanCursor<ByteArray>>) =
        { key, cursor ->
            cursor?.let { c -> this.async().sscan(key.toByteArray(), c) } ?: this.async()
                .sscan(key.toByteArray())
        }

    override fun collectValuesIntoResult(
        cursor: ValueScanCursor<ByteArray>,
        collectedResult: MutableList<ByteArray>
    ): MutableList<ByteArray> {
        collectedResult.addAll(cursor.values)
        return collectedResult
    }

    override fun createResultCollector(): MutableList<ByteArray> {
        return mutableListOf()
    }

    override fun size(result: MutableList<ByteArray>): Int {
        return result.size
    }
}
