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
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class RedisToJavaConverterTest {

    @Test
    fun `should convert byte array to string`() {
        val value = "test".toByteArray()

        val result = RedisToJavaConverter().convert(value)

        assertEquals("test", result)
    }

    @Test
    fun `should convert scored value to string`() {
        val value = ScoredValue.just(10.0, "scoredValue".toByteArray())

        val result = RedisToJavaConverter().convert(value)

        assertEquals(10.0 to "scoredValue", result)
    }

    @Test
    fun `should convert pair value to string`() {
        val value = "key".toByteArray() to "value".toByteArray()

        val result = RedisToJavaConverter().convert(value)

        assertEquals("key" to "value", result)
    }

    @Test
    fun `should get bytes from byte array string`() {
        val value = "test".toByteArray()

        val result = RedisToJavaConverter().getBytesCount(value)

        assertEquals(4, result)
    }

    @Test
    fun `should get bytes from scored value to string`() {
        val value = ScoredValue.just(3.0, "scoredValue".toByteArray())

        val result = RedisToJavaConverter().getBytesCount(value)

        assertEquals(19, result)
    }

    @Test
    fun `should get bytes from map value to string`() {
        val value = "key".toByteArray() to "value".toByteArray()

        val result = RedisToJavaConverter().getBytesCount(value)

        assertEquals(8, result)
    }
}
