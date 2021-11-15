package io.qalipsis.plugins.redis.lettuce.poll.scanners

import assertk.all
import assertk.assertThat
import assertk.assertions.*
import io.lettuce.core.ValueScanCursor
import io.mockk.*
import io.qalipsis.api.sync.asSuspended
import io.qalipsis.plugins.redis.lettuce.poll.PollRawResult
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.coVerifyOnce
import io.qalipsis.test.mockk.relaxedMockk
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.time.Duration

@WithMockk
internal class LettuceSScannerTest :
    AbstractLettuceScannerTest<ValueScanCursor<ByteArray>, MutableList<ByteArray>, LettuceSScanner>() {

    @Test
    @Timeout(5)
    override fun `should not forward an empty result`() = testDispatcherProvider.runTest {
        // given
        coEvery { singleConnection.async().sscan(any()).asSuspended().get(any()) } returns cursor
        every { cursor.isFinished } returns true
        every { cursor.values } returns emptyList()

        // when
        scanner.execute(singleConnection, "key", resultsChannel, tags)

        // then
        coVerifyOnce {
            eventsLogger.trace("lettuce.poll.polling", null, any(), tags = refEq(tags))
            singleConnection.async().sscan("key".toByteArray())
            eventsLogger.info("lettuce.poll.response", any<Array<*>>(), any(), tags = refEq(tags))
        }
        confirmVerified(resultsChannel)
    }

    @Test
    @Timeout(5)
    override fun `should not return anything when an exception occurs`() = testDispatcherProvider.runTest {
        // given
        every { singleConnection.async().sscan(any()) } throws RuntimeException()

        // when
        scanner.execute(singleConnection, "key", resultsChannel, tags)

        // then
        coVerifyOnce {
            eventsLogger.trace("lettuce.poll.polling", null, any(), tags = refEq(tags))
            singleConnection.async().sscan("key".toByteArray())
            eventsLogger.warn("lettuce.poll.failure", any<Array<*>>(), any(), tags = refEq(tags))
        }
        confirmVerified(resultsChannel)
    }

    @Test
    @Timeout(5)
    override fun `should execute on cluster connection until cursor is finished`() = testDispatcherProvider.runTest {
        // given
        val cursor1 = relaxedMockk<ValueScanCursor<ByteArray>> {
            every { isFinished } returns false
            every { values } returns listOf(
                "value-1".toByteArray(),
                "value-2".toByteArray()
            )
        }
        val cursor2 = relaxedMockk<ValueScanCursor<ByteArray>> {
            every { isFinished } returns false
            every { values } returns listOf(
                "value-3".toByteArray(),
                "value-4".toByteArray()
            )
        }
        val cursor3 = relaxedMockk<ValueScanCursor<ByteArray>> {
            every { isFinished } returns true
            every { values } returns listOf("value-5".toByteArray())
        }
        coEvery { clusterConnection.async().sscan(any()).asSuspended().get(any()) } returns cursor1
        coEvery { clusterConnection.async().sscan(any(), refEq(cursor1)).asSuspended().get(any()) } returns cursor2
        coEvery { clusterConnection.async().sscan(any(), refEq(cursor2)).asSuspended().get(any()) } returns cursor3

        // when
        scanner.execute(clusterConnection, "key", resultsChannel, tags)

        // then
        val resultsCaptor = slot<PollRawResult<List<ByteArray>>>()
        coVerifyOrder {
            eventsLogger.trace("lettuce.poll.polling", null, any(), tags = refEq(tags))
            clusterConnection.async().sscan(eq("key".toByteArray()))
            clusterConnection.async().sscan(eq("key".toByteArray()), refEq(cursor1))
            clusterConnection.async().sscan(eq("key".toByteArray()), refEq(cursor2))
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
        confirmVerified(resultsChannel)
    }

    @Test
    @Timeout(5)
    override fun `should execute on single connection until cursor is finished`() = testDispatcherProvider.runTest {
        // given
        val cursor1 = relaxedMockk<ValueScanCursor<ByteArray>> {
            every { isFinished } returns false
            every { values } returns listOf(
                "value-1".toByteArray(),
                "value-2".toByteArray()
            )
        }
        val cursor2 = relaxedMockk<ValueScanCursor<ByteArray>> {
            every { isFinished } returns false
            every { values } returns listOf(
                "value-3".toByteArray(),
                "value-4".toByteArray()
            )
        }
        val cursor3 = relaxedMockk<ValueScanCursor<ByteArray>> {
            every { isFinished } returns true
            every { values } returns listOf("value-5".toByteArray())
        }
        coEvery { singleConnection.async().sscan(any()).asSuspended().get(any()) } returns cursor1
        coEvery { singleConnection.async().sscan(any(), refEq(cursor1)).asSuspended().get(any()) } returns cursor2
        coEvery { singleConnection.async().sscan(any(), refEq(cursor2)).asSuspended().get(any()) } returns cursor3

        // when
        scanner.execute(singleConnection, "key", resultsChannel, tags)

        // then
        val resultsCaptor = slot<PollRawResult<List<ByteArray>>>()
        coVerifyOrder {
            eventsLogger.trace("lettuce.poll.polling", null, any(), tags = refEq(tags))
            singleConnection.async().sscan(eq("key".toByteArray()))
            singleConnection.async().sscan(eq("key".toByteArray()), refEq(cursor1))
            singleConnection.async().sscan(eq("key".toByteArray()), refEq(cursor2))
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
        confirmVerified(resultsChannel)
    }
}
