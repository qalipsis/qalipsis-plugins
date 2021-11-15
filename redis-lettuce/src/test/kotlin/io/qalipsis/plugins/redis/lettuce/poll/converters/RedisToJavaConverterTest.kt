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
