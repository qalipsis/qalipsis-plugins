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
