/*
 * QALIPSIS
 * Copyright (C) 2025 AERIS IT Solutions GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

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
