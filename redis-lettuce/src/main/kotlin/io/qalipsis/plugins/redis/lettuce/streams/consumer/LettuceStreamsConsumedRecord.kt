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

package io.qalipsis.plugins.redis.lettuce.streams.consumer

/**
 * Qalipsis representation of a consumed Redis Streams record.
 *
 * @property offset of the record consumed by Qalipsis.
 * @property consumedTimestamp timestamp when the message was consumed by Qalipsis.
 * @property streamKey from where the message was consumed.
 * @property id of the message.
 * @property value of the record.
 *
 * @author Gabriel Moraes
 */
data class LettuceStreamsConsumedRecord(
    val offset: Long,
    val consumedTimestamp: Long,
    val id: String,
    val streamKey: String,
    val value: Map<String, String>,
) {
    internal constructor(offset: Long, id: String, stream: ByteArray, value: Map<String, String>) : this(
        offset = offset,
        consumedTimestamp = System.currentTimeMillis(),
        id = id,
        streamKey = stream.decodeToString(),
        value = value
    )
}
