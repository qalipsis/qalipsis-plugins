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

import assertk.all
import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import assertk.assertions.prop
import io.lettuce.core.MapScanCursor
import io.mockk.*
import io.qalipsis.api.sync.asSuspended
import io.qalipsis.plugins.redis.lettuce.poll.PollRawResult
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.coVerifyOnce
import io.qalipsis.test.mockk.relaxedMockk
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.time.Duration

@WithMockk
internal class LettuceHScannerTest :
    AbstractLettuceScannerTest<MapScanCursor<ByteArray, ByteArray>, MutableMap<ByteArray, ByteArray>, LettuceHScanner>() {

    @Test
    @Timeout(5)
    override fun `should not forward an empty result`() = testDispatcherProvider.runTest {
        // given
        coEvery { singleConnection.async().hscan(any()).asSuspended().get(any()) } returns cursor
        every { cursor.isFinished } returns true
        every { cursor.map } returns emptyMap()

        // when
        scanner.execute(singleConnection, "key", resultsChannel, tags)

        // then
        coVerifyOnce {
            eventsLogger.trace("redis.lettuce.poll.polling", null, any(), tags = refEq(tags))
            singleConnection.async().hscan(eq("key".toByteArray()))
            eventsLogger.info("redis.lettuce.poll.response", any<Array<*>>(), any(), tags = refEq(tags))
        }
        confirmVerified(resultsChannel, singleConnection, clusterConnection)
    }

    @Test
    @Timeout(5)
    override fun `should not return anything when an exception occurs`() = testDispatcherProvider.runTest {
        // given
        every { singleConnection.async().hscan(any()) } throws RuntimeException()

        // when
        scanner.execute(singleConnection, "key", resultsChannel, tags)

        // then
        coVerifyOnce {
            eventsLogger.trace("redis.lettuce.poll.polling", null, any(), tags = refEq(tags))
            singleConnection.async().hscan(eq("key".toByteArray()))
            eventsLogger.warn("redis.lettuce.poll.failure", any<Array<*>>(), any(), tags = refEq(tags))
        }
        confirmVerified(resultsChannel, singleConnection, clusterConnection)
    }

    @Test
    @Timeout(5)
    override fun `should execute on cluster connection until cursor is finished`() = testDispatcherProvider.runTest {
        // given
        val cursor1 = relaxedMockk<MapScanCursor<ByteArray, ByteArray>> {
            every { isFinished } returns false
            every { map } returns mapOf(
                "1".toByteArray() to "value-1".toByteArray(),
                "2".toByteArray() to "value-2".toByteArray()
            )
        }
        val cursor2 = relaxedMockk<MapScanCursor<ByteArray, ByteArray>> {
            every { isFinished } returns false
            every { map } returns mapOf(
                "3".toByteArray() to "value-3".toByteArray(),
                "4".toByteArray() to "value-4".toByteArray()
            )
        }
        val cursor3 = relaxedMockk<MapScanCursor<ByteArray, ByteArray>> {
            every { isFinished } returns true
            every { map } returns mapOf("5".toByteArray() to "value-5".toByteArray())
        }
        coEvery { clusterConnection.async().hscan(any()).asSuspended().get(any()) } returns cursor1
        coEvery { clusterConnection.async().hscan(any(), refEq(cursor1)).asSuspended().get(any()) } returns cursor2
        coEvery { clusterConnection.async().hscan(any(), refEq(cursor2)).asSuspended().get(any()) } returns cursor3

        // when
        scanner.execute(clusterConnection, "key", resultsChannel, tags)

        // then
        val resultsCaptor = slot<PollRawResult<MutableMap<ByteArray, ByteArray>>>()
        coVerifyOrder {
            eventsLogger.trace("redis.lettuce.poll.polling", null, any(), tags = refEq(tags))
            clusterConnection.async().hscan(eq("key".toByteArray()))
            clusterConnection.async().hscan(eq("key".toByteArray()), refEq(cursor1))
            clusterConnection.async().hscan(eq("key".toByteArray()), refEq(cursor2))
            eventsLogger.info("redis.lettuce.poll.response", any<Array<*>>(), any(), tags = refEq(tags))
            resultsChannel.send(capture(resultsCaptor))
        }

        val expected = mapOf(
            "1" to "value-1",
            "2" to "value-2",
            "3" to "value-3",
            "4" to "value-4",
            "5" to "value-5"
        )
        assertThat(resultsCaptor.captured).all {
            prop(PollRawResult<MutableMap<ByteArray, ByteArray>>::records).transform {
                it.mapKeys { String(it.key) }.mapValues { String(it.value) }
            }.all {
                hasSize(5)
                isEqualTo(expected)
            }
            prop(PollRawResult<MutableMap<ByteArray, ByteArray>>::pollCount).isEqualTo(3)
            prop(PollRawResult<MutableMap<ByteArray, ByteArray>>::recordsCount).isEqualTo(5)
            prop(PollRawResult<MutableMap<ByteArray, ByteArray>>::timeToResult).isGreaterThan(Duration.ZERO)
        }
        confirmVerified(resultsChannel, singleConnection, clusterConnection)
    }

    @Test
    @Timeout(5)
    override fun `should execute on single connection until cursor is finished`() = testDispatcherProvider.runTest {
        // given
        val cursor1 = relaxedMockk<MapScanCursor<ByteArray, ByteArray>> {
            every { isFinished } returns false
            every { map } returns mapOf(
                "1".toByteArray() to "value-1".toByteArray(),
                "2".toByteArray() to "value-2".toByteArray()
            )
        }
        val cursor2 = relaxedMockk<MapScanCursor<ByteArray, ByteArray>> {
            every { isFinished } returns false
            every { map } returns mapOf(
                "3".toByteArray() to "value-3".toByteArray(),
                "4".toByteArray() to "value-4".toByteArray()
            )
        }
        val cursor3 = relaxedMockk<MapScanCursor<ByteArray, ByteArray>> {
            every { isFinished } returns true
            every { map } returns mapOf("5".toByteArray() to "value-5".toByteArray())
        }
        coEvery { singleConnection.async().hscan(any()).asSuspended().get(any()) } returns cursor1
        coEvery { singleConnection.async().hscan(any(), refEq(cursor1)).asSuspended().get(any()) } returns cursor2
        coEvery { singleConnection.async().hscan(any(), refEq(cursor2)).asSuspended().get(any()) } returns cursor3

        // when
        scanner.execute(singleConnection, "key", resultsChannel, tags)

        // then
        val resultsCaptor = slot<PollRawResult<MutableMap<ByteArray, ByteArray>>>()
        coVerifyOrder {
            eventsLogger.trace("redis.lettuce.poll.polling", null, any(), tags = refEq(tags))
            singleConnection.async().hscan(eq("key".toByteArray()))
            singleConnection.async().hscan(eq("key".toByteArray()), refEq(cursor1))
            singleConnection.async().hscan(eq("key".toByteArray()), refEq(cursor2))
            eventsLogger.info("redis.lettuce.poll.response", any<Array<*>>(), any(), tags = refEq(tags))
            resultsChannel.send(capture(resultsCaptor))
        }

        val expected = mapOf(
            "1" to "value-1",
            "2" to "value-2",
            "3" to "value-3",
            "4" to "value-4",
            "5" to "value-5"
        )
        assertThat(resultsCaptor.captured).all {
            prop(PollRawResult<MutableMap<ByteArray, ByteArray>>::records).transform {
                it.mapKeys { String(it.key) }.mapValues { String(it.value) }
            }.all {
                hasSize(5)
                isEqualTo(expected)
            }
            prop(PollRawResult<MutableMap<ByteArray, ByteArray>>::pollCount).isEqualTo(3)
            prop(PollRawResult<MutableMap<ByteArray, ByteArray>>::recordsCount).isEqualTo(5)
            prop(PollRawResult<MutableMap<ByteArray, ByteArray>>::timeToResult).isGreaterThan(Duration.ZERO)
        }
        confirmVerified(resultsChannel, singleConnection, clusterConnection)
    }

}
