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

import assertk.all
import assertk.assertThat
import assertk.assertions.*
import io.lettuce.core.ScoredValue
import io.lettuce.core.ScoredValueScanCursor
import io.mockk.*
import io.qalipsis.api.sync.asSuspended
import io.qalipsis.plugins.redis.lettuce.poll.PollRawResult
import io.qalipsis.test.assertk.prop
import io.qalipsis.test.assertk.typedProp
import io.qalipsis.test.mockk.coVerifyOnce
import io.qalipsis.test.mockk.relaxedMockk
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.time.Duration

internal class LettuceZScannerTest :
    AbstractLettuceScannerTest<ScoredValueScanCursor<ByteArray>, MutableList<ScoredValue<ByteArray>>, LettuceZScanner>() {

    @Test
    @Timeout(5)
    override fun `should not forward an empty result`() = testDispatcherProvider.runTest {
        // given
        coEvery { singleConnection.async().zscan(any()).asSuspended().get(any()) } returns cursor
        every { cursor.isFinished } returns true
        every { cursor.values } returns emptyList()

        // when
        scanner.execute(singleConnection, "key", resultsChannel, tags)

        // then
        coVerifyOnce {
            eventsLogger.trace("redis.lettuce.poll.polling", null, any(), tags = refEq(tags))
            singleConnection.async().zscan("key".toByteArray())
            eventsLogger.info("redis.lettuce.poll.response", any<Array<*>>(), any(), tags = refEq(tags))
        }
        confirmVerified(resultsChannel)
    }

    @Test
    @Timeout(5)
    override fun `should not return anything when an exception occurs`() = testDispatcherProvider.runTest {
        // given
        every { singleConnection.async().zscan(any()) } throws RuntimeException()

        // when
        scanner.execute(singleConnection, "key", resultsChannel, tags)

        // then
        coVerifyOnce {
            eventsLogger.trace("redis.lettuce.poll.polling", null, any(), tags = refEq(tags))
            singleConnection.async().zscan("key".toByteArray())
            eventsLogger.warn("redis.lettuce.poll.failure", any<Array<*>>(), any(), tags = refEq(tags))
        }
        confirmVerified(resultsChannel)
    }

    @Test
    @Timeout(5)
    override fun `should execute on cluster connection until cursor is finished`() = testDispatcherProvider.runTest {
        // given
        val cursor1 = relaxedMockk<ScoredValueScanCursor<ByteArray>> {
            every { isFinished } returns false
            every { values } returns listOf(
                ScoredValue.just(1.0, "value-1".toByteArray()),
                ScoredValue.just(2.0, "value-2".toByteArray())
            )
        }
        val cursor2 = relaxedMockk<ScoredValueScanCursor<ByteArray>> {
            every { isFinished } returns false
            every { values } returns listOf(
                ScoredValue.just(3.0, "value-3".toByteArray()),
                ScoredValue.just(4.0, "value-4".toByteArray())
            )
        }
        val cursor3 = relaxedMockk<ScoredValueScanCursor<ByteArray>> {
            every { isFinished } returns true
            every { values } returns listOf(ScoredValue.just(5.0, "value-5".toByteArray()))
        }
        coEvery { clusterConnection.async().zscan(any()).asSuspended().get(any()) } returns cursor1
        coEvery { clusterConnection.async().zscan(any(), refEq(cursor1)).asSuspended().get(any()) } returns cursor2
        coEvery { clusterConnection.async().zscan(any(), refEq(cursor2)).asSuspended().get(any()) } returns cursor3

        // when
        scanner.execute(clusterConnection, "key", resultsChannel, tags)

        // then
        val resultsCaptor = slot<PollRawResult<List<ScoredValue<ByteArray>>>>()
        coVerifyOrder {
            eventsLogger.trace("redis.lettuce.poll.polling", null, any(), tags = refEq(tags))
            clusterConnection.async().zscan(eq("key".toByteArray()))
            clusterConnection.async().zscan(eq("key".toByteArray()), refEq(cursor1))
            clusterConnection.async().zscan(eq("key".toByteArray()), refEq(cursor2))
            eventsLogger.info("redis.lettuce.poll.response", any<Array<*>>(), any(), tags = refEq(tags))
            resultsChannel.send(capture(resultsCaptor))
        }

        assertThat(resultsCaptor.captured).all {
            prop(PollRawResult<List<ScoredValue<ByteArray>>>::records).all {
                hasSize(5)
                repeat(5) { index ->
                    index(index).all {
                        prop("score").isEqualTo((index + 1).toDouble())
                        typedProp<ByteArray>("value").transform { String(it) }.isEqualTo("value-${index + 1}")
                    }
                }
            }
            prop(PollRawResult<List<ScoredValue<ByteArray>>>::pollCount).isEqualTo(3)
            prop(PollRawResult<List<ScoredValue<ByteArray>>>::recordsCount).isEqualTo(5)
            prop(PollRawResult<List<ScoredValue<ByteArray>>>::timeToResult).isGreaterThan(Duration.ZERO)
        }
        confirmVerified(resultsChannel)
    }

    @Test
    @Timeout(5)
    override fun `should execute on single connection until cursor is finished`() = testDispatcherProvider.runTest {
        // given
        val cursor1 = relaxedMockk<ScoredValueScanCursor<ByteArray>> {
            every { isFinished } returns false
            every { values } returns listOf(
                ScoredValue.just(1.0, "value-1".toByteArray()),
                ScoredValue.just(2.0, "value-2".toByteArray())
            )
        }
        val cursor2 = relaxedMockk<ScoredValueScanCursor<ByteArray>> {
            every { isFinished } returns false
            every { values } returns listOf(
                ScoredValue.just(3.0, "value-3".toByteArray()),
                ScoredValue.just(4.0, "value-4".toByteArray())
            )
        }
        val cursor3 = relaxedMockk<ScoredValueScanCursor<ByteArray>> {
            every { isFinished } returns true
            every { values } returns listOf(ScoredValue.just(5.0, "value-5".toByteArray()))
        }
        coEvery { singleConnection.async().zscan(any()).asSuspended().get(any()) } returns cursor1
        coEvery { singleConnection.async().zscan(any(), refEq(cursor1)).asSuspended().get(any()) } returns cursor2
        coEvery { singleConnection.async().zscan(any(), refEq(cursor2)).asSuspended().get(any()) } returns cursor3

        // when
        scanner.execute(singleConnection, "key", resultsChannel, tags)

        // then
        val resultsCaptor = slot<PollRawResult<List<ScoredValue<ByteArray>>>>()
        coVerifyOrder {
            eventsLogger.trace("redis.lettuce.poll.polling", null, any(), tags = refEq(tags))
            singleConnection.async().zscan(eq("key".toByteArray()))
            singleConnection.async().zscan(eq("key".toByteArray()), refEq(cursor1))
            singleConnection.async().zscan(eq("key".toByteArray()), refEq(cursor2))
            eventsLogger.info("redis.lettuce.poll.response", any<Array<*>>(), any(), tags = refEq(tags))
            resultsChannel.send(capture(resultsCaptor))
        }

        assertThat(resultsCaptor.captured).all {
            prop(PollRawResult<List<ScoredValue<ByteArray>>>::records).all {
                hasSize(5)
                repeat(5) { index ->
                    index(index).all {
                        prop("score").isEqualTo((index + 1).toDouble())
                        typedProp<ByteArray>("value").transform { String(it) }.isEqualTo("value-${index + 1}")
                    }
                }
            }
            prop(PollRawResult<List<ScoredValue<ByteArray>>>::pollCount).isEqualTo(3)
            prop(PollRawResult<List<ScoredValue<ByteArray>>>::recordsCount).isEqualTo(5)
            prop(PollRawResult<List<ScoredValue<ByteArray>>>::timeToResult).isGreaterThan(Duration.ZERO)
        }
        confirmVerified(resultsChannel)
    }
}

