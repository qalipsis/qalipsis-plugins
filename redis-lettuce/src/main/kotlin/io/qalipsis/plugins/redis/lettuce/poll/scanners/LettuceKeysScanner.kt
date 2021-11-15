package io.qalipsis.plugins.redis.lettuce.poll.scanners

import io.lettuce.core.KeyScanCursor
import io.lettuce.core.RedisFuture
import io.lettuce.core.ScanArgs
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.cluster.api.StatefulRedisClusterConnection
import io.qalipsis.api.events.EventsLogger
import io.qalipsis.plugins.redis.lettuce.poll.LettuceScanner
import io.qalipsis.plugins.redis.lettuce.poll.RedisLettuceScanMethod

/**
 * Implementation of [LettuceScanner] for the SCAN type.
 *
 * @author Gabriel Moraes
 * @author Eric Jessé
 */
internal class LettuceKeysScanner(eventsLogger: EventsLogger?) :
    AbstractLettuceScanner<KeyScanCursor<ByteArray>, MutableList<ByteArray>>(eventsLogger) {

    override fun getType() = RedisLettuceScanMethod.SCAN

    override val clusterAction: StatefulRedisClusterConnection<ByteArray, ByteArray>.(pattern: String, cursor: KeyScanCursor<ByteArray>?) -> RedisFuture<KeyScanCursor<ByteArray>> =
        { pattern, cursor ->
            cursor?.let { c -> this.async().scan(c, ScanArgs().match(pattern)) } ?: this.async()
                .scan(ScanArgs().match(pattern))
        }
    override val singleNodeAction: StatefulRedisConnection<ByteArray, ByteArray>.(pattern: String, cursor: KeyScanCursor<ByteArray>?) -> RedisFuture<KeyScanCursor<ByteArray>> =
        { pattern, cursor ->
            cursor?.let { c -> this.async().scan(c, ScanArgs().match(pattern)) } ?: this.async()
                .scan(ScanArgs().match(pattern))
        }

    override fun collectValuesIntoResult(
        cursor: KeyScanCursor<ByteArray>,
        collectedResult: MutableList<ByteArray>
    ): MutableList<ByteArray> {
        collectedResult.addAll(cursor.keys)
        return collectedResult
    }

    override fun createResultCollector(): MutableList<ByteArray> {
        return mutableListOf()
    }

    override fun size(result: MutableList<ByteArray>): Int {
        return result.size
    }

}
