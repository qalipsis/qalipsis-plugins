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
import io.lettuce.core.KeyScanCursor
import io.lettuce.core.ScanArgs
import io.mockk.*
import io.qalipsis.api.sync.asSuspended
import io.qalipsis.plugins.redis.lettuce.poll.PollRawResult
import io.qalipsis.test.assertk.typedProp
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.coVerifyOnce
import io.qalipsis.test.mockk.relaxedMockk
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.time.Duration

@WithMockk
internal class LettuceKeysScannerTest :
    AbstractLettuceScannerTest<KeyScanCursor<ByteArray>, MutableList<ByteArray>, LettuceKeysScanner>() {

    @Test
    @Timeout(5)
    override fun `should not forward an empty result`() = testDispatcherProvider.runTest {
        // given
        coEvery { singleConnection.async().scan(any<ScanArgs>()).asSuspended().get(any()) } returns cursor
        every { cursor.isFinished } returns true
        every { cursor.keys } returns emptyList()

        // when
        scanner.execute(singleConnection, "pattern", resultsChannel, tags)

        // then
        val scanArgument = slot<ScanArgs>()
        coVerifyOnce {
            eventsLogger.trace("lettuce.poll.polling", null, any(), tags = refEq(tags))
            singleConnection.async().scan(capture(scanArgument))
            eventsLogger.info("lettuce.poll.response", any<Array<*>>(), any(), tags = refEq(tags))
        }
        assertThat(scanArgument.captured).typedProp<ByteArray>("match").isEqualTo("pattern".toByteArray())
        confirmVerified(resultsChannel)
    }

    @Test
    @Timeout(5)
    override fun `should not return anything when an exception occurs`() = testDispatcherProvider.runTest {
        // given
        every { singleConnection.async().scan(any<ScanArgs>()) } throws RuntimeException()

        // when
        scanner.execute(singleConnection, "pattern", resultsChannel, tags)

        // then
        val scanArgument = slot<ScanArgs>()
        coVerifyOnce {
            eventsLogger.trace("lettuce.poll.polling", null, any(), tags = refEq(tags))
            singleConnection.async().scan(capture(scanArgument))
            eventsLogger.warn("lettuce.poll.failure", any<Array<*>>(), any(), tags = refEq(tags))
        }
        assertThat(scanArgument.captured).typedProp<ByteArray>("match").isEqualTo("pattern".toByteArray())
        confirmVerified(resultsChannel)
    }

    @Test
    @Timeout(5)
    override fun `should execute on cluster connection until cursor is finished`() = testDispatcherProvider.runTest {
        // given
        val cursor1 = relaxedMockk<KeyScanCursor<ByteArray>> {
            every { isFinished } returns false
            every { keys } returns listOf(
                "value-1".toByteArray(),
                "value-2".toByteArray()
            )
        }
        val cursor2 = relaxedMockk<KeyScanCursor<ByteArray>> {
            every { isFinished } returns false
            every { keys } returns listOf(
                "value-3".toByteArray(),
                "value-4".toByteArray()
            )
        }
        val cursor3 = relaxedMockk<KeyScanCursor<ByteArray>> {
            every { isFinished } returns true
            every { keys } returns listOf("value-5".toByteArray())
        }
        coEvery { clusterConnection.async().scan(any<ScanArgs>()).asSuspended().get(any()) } returns cursor1
        coEvery { clusterConnection.async().scan(refEq(cursor1), any()).asSuspended().get(any()) } returns cursor2
        coEvery { clusterConnection.async().scan(refEq(cursor2), any()).asSuspended().get(any()) } returns cursor3

        // when
        scanner.execute(clusterConnection, "pattern", resultsChannel, tags)

        // then
        val scanArguments = mutableListOf<ScanArgs>()
        val resultsCaptor = slot<PollRawResult<List<ByteArray>>>()
        coVerifyOrder {
            eventsLogger.trace("lettuce.poll.polling", null, any(), tags = refEq(tags))
            clusterConnection.async().scan(capture(scanArguments))
            clusterConnection.async().scan(refEq(cursor1), capture(scanArguments))
            clusterConnection.async().scan(refEq(cursor2), capture(scanArguments))
            eventsLogger.info("lettuce.poll.response", any<Array<*>>(), any(), tags = refEq(tags))
            resultsChannel.send(capture(resultsCaptor))
        }

        assertThat(resultsCaptor.captured).all {
            prop(PollRawResult<List<ByteArray>>::records).transform { it.map { String(it) } }.all {
                hasSize(5)
                containsExactly(
                    "value-1",
                    "value-2",
                    "value-3",
                    "value-4",
                    "value-5"
                )
            }
            prop(PollRawResult<List<ByteArray>>::pollCount).isEqualTo(3)
            prop(PollRawResult<List<ByteArray>>::recordsCount).isEqualTo(5)
            prop(PollRawResult<List<ByteArray>>::timeToResult).isGreaterThan(Duration.ZERO)
        }
        assertThat(scanArguments).each { it.typedProp<ByteArray>("match").isEqualTo("pattern".toByteArray()) }
        confirmVerified(resultsChannel)
    }

    @Test
    @Timeout(5)
    override fun `should execute on single connection until cursor is finished`() = testDispatcherProvider.runTest {
        // given
        val cursor1 = relaxedMockk<KeyScanCursor<ByteArray>> {
            every { isFinished } returns false
            every { keys } returns listOf(
                "value-1".toByteArray(),
                "value-2".toByteArray()
            )
        }
        val cursor2 = relaxedMockk<KeyScanCursor<ByteArray>> {
            every { isFinished } returns false
            every { keys } returns listOf(
                "value-3".toByteArray(),
                "value-4".toByteArray()
            )
        }
        val cursor3 = relaxedMockk<KeyScanCursor<ByteArray>> {
            every { isFinished } returns true
            every { keys } returns listOf("value-5".toByteArray())
        }
        coEvery { singleConnection.async().scan(any<ScanArgs>()).asSuspended().get(any()) } returns cursor1
        coEvery { singleConnection.async().scan(refEq(cursor1), any()).asSuspended().get(any()) } returns cursor2
        coEvery { singleConnection.async().scan(refEq(cursor2), any()).asSuspended().get(any()) } returns cursor3

        // when
        scanner.execute(singleConnection, "pattern", resultsChannel, tags)

        // then
        val scanArguments = mutableListOf<ScanArgs>()
        val resultsCaptor = slot<PollRawResult<List<ByteArray>>>()
        coVerifyOrder {
            eventsLogger.trace("lettuce.poll.polling", null, any(), tags = refEq(tags))
            singleConnection.async().scan(capture(scanArguments))
            singleConnection.async().scan(refEq(cursor1), capture(scanArguments))
            singleConnection.async().scan(refEq(cursor2), capture(scanArguments))
            eventsLogger.info("lettuce.poll.response", any<Array<*>>(), any(), tags = refEq(tags))
            resultsChannel.send(capture(resultsCaptor))
        }

        assertThat(resultsCaptor.captured).all {
            prop(PollRawResult<List<ByteArray>>::records).transform { it.map { String(it) } }.all {
                hasSize(5)
                containsExactly(
                    "value-1",
                    "value-2",
                    "value-3",
                    "value-4",
                    "value-5"
                )
            }
            prop(PollRawResult<List<ByteArray>>::pollCount).isEqualTo(3)
            prop(PollRawResult<List<ByteArray>>::recordsCount).isEqualTo(5)
            prop(PollRawResult<List<ByteArray>>::timeToResult).isGreaterThan(Duration.ZERO)
        }
        assertThat(scanArguments).each { it.typedProp<ByteArray>("match").isEqualTo("pattern".toByteArray()) }
        confirmVerified(resultsChannel)
    }

}
