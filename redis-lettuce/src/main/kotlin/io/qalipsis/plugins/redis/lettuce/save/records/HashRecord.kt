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

package io.qalipsis.plugins.redis.lettuce.save.records

import io.qalipsis.plugins.redis.lettuce.save.LettuceSaveRecord
import io.qalipsis.plugins.redis.lettuce.save.RedisLettuceSaveMethod

/**
 * Qalipsis representation of a Redis record to save using HSET command.
 *
 * @property key of the record.
 * @property value of record.
 * @property redisMethod of the record to be saved.
 *
 * @author Gabriel Moraes
 */
data class HashRecord internal constructor(
    override val key: String,
    override val value: Map<String, String>,
    override var redisMethod: RedisLettuceSaveMethod
) : LettuceSaveRecord<Map<String, String>> {

    constructor(key: String, value: Map<String, String>): this(key, value, RedisLettuceSaveMethod.HSET)

    override fun getRecordBytesSize(): Int {
        return value.map { it.key.toByteArray().size + it.value.toByteArray().size }.sum() + key.toByteArray().size
    }
}
