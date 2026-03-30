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
