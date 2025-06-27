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

package io.qalipsis.plugins.redis.lettuce.poll.converters

import io.lettuce.core.ScoredValue
import io.qalipsis.api.annotations.PluginComponent

/**
 * Converter of the redis return type to the specification type.
 *
 * This class also calculates the size in bytes for the redis return type.
 *
 * @author Gabriel Moraes
 */
@PluginComponent
internal class RedisToJavaConverter {

    /**
     * Converts the value of redis result to the specification return type for each command.
     *
     * Each redis command implementation has an specific return type, there are 3 supported types:
     * [ScoredValue] from the ZSCAN command is converted to a [Pair] with score as [Double] and the value as [String].
     * [Pair] from the HSCAN command is converted to a [Pair] with the key as [String] and the value as [String].
     * [ByteArray] from the SSCAN and SCAN commands is converted to [String].
     *
     * @param value of redis commands return types.
     * @return specific return type for each specification converted from the [value].
     */
    fun convert(value: Any?): Any? {
        return when (value) {
            is ScoredValue<*> -> value.score to convert(value.value)
            is Pair<*, *> -> convert(value.first) to convert(value.second)
            is ByteArray -> String(value, Charsets.UTF_8)
            else -> null
        }
    }

    /**
     * Calculates the size in bytes for each value of redis result.
     *
     * The supported types for calculation are [ScoredValue] from ZSCAN, [Map] from HSCAN and [ByteArray] from SSCAN and SCAN.
     *
     * @param value of redis commands return types.
     * @return size in bytes for the [value].
     */
    fun getBytesCount(value: Any?): Int {
        return when (value) {
            is ScoredValue<*> -> Double.SIZE_BYTES + getBytesCount(value.value) // Score is a double + size of the value.
            is Pair<*, *> -> getBytesCount(value.first) + getBytesCount(value.second) // Sum of the sizes of the key and value.
            is ByteArray -> value.size
            else -> 0
        }
    }
}
