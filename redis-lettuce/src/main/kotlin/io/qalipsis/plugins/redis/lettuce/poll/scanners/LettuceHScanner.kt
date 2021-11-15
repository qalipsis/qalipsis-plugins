package io.qalipsis.plugins.redis.lettuce.poll.scanners

import io.lettuce.core.MapScanCursor
import io.lettuce.core.RedisFuture
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.cluster.api.StatefulRedisClusterConnection
import io.qalipsis.api.events.EventsLogger
import io.qalipsis.plugins.redis.lettuce.poll.LettuceScanner
import io.qalipsis.plugins.redis.lettuce.poll.RedisLettuceScanMethod

/**
 * Implementation of [LettuceScanner] for the HSCAN type.
 *
 * @author Gabriel Moraes
 * @author Eric Jessé
 */
internal class LettuceHScanner(eventsLogger: EventsLogger?) :
    AbstractLettuceScanner<MapScanCursor<ByteArray, ByteArray>, MutableMap<ByteArray, ByteArray>>(
        eventsLogger
    ) {

    override fun getType() = RedisLettuceScanMethod.HSCAN

    override val clusterAction: StatefulRedisClusterConnection<ByteArray, ByteArray>.(key: String, cursor: MapScanCursor<ByteArray, ByteArray>?) -> RedisFuture<MapScanCursor<ByteArray, ByteArray>> =
        { key, cursor ->
            cursor?.let { c -> this.async().hscan(key.toByteArray(), c) } ?: this.async()
                .hscan(key.toByteArray())
        }
    override val singleNodeAction: StatefulRedisConnection<ByteArray, ByteArray>.(key: String, cursor: MapScanCursor<ByteArray, ByteArray>?) -> RedisFuture<MapScanCursor<ByteArray, ByteArray>> =
        { key, cursor ->
            cursor?.let { c -> this.async().hscan(key.toByteArray(), c) } ?: this.async()
                .hscan(key.toByteArray())
        }

    override fun collectValuesIntoResult(
        cursor: MapScanCursor<ByteArray, ByteArray>,
        collectedResult: MutableMap<ByteArray, ByteArray>
    ): MutableMap<ByteArray, ByteArray> {
        collectedResult.putAll(cursor.map); return collectedResult
    }

    override fun createResultCollector(): MutableMap<ByteArray, ByteArray> {
        return mutableMapOf()
    }

    override fun size(result: MutableMap<ByteArray, ByteArray>): Int {
        return result.size
    }

}
