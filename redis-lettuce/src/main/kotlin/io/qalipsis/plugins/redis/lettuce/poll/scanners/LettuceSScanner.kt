/*
 * Copyright 2022 AERIS IT Solutions GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing
 * permissions and limitations under the License.
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
