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

package io.qalipsis.plugins.sql.poll

import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.every
import io.mockk.spyk
import io.qalipsis.api.sync.SuspendedCountLatch
import io.qalipsis.plugins.sql.SqlResultSet
import io.qalipsis.plugins.sql.dialect.Dialect
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.CleanMockkRecordedCalls
import io.qalipsis.test.mockk.coVerifyNever
import io.qalipsis.test.mockk.relaxedMockk
import io.r2dbc.pool.ConnectionPool
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.Duration

/**
 *
 * @author Eric Jesse
 */
@Suppress("EXPERIMENTAL_API_USAGE")
@CleanMockkRecordedCalls
internal class SqlIterativeReaderTest {

    @JvmField
    @RegisterExtension
    val testDispatcherProvider = TestDispatcherProvider()

    val sqlPollStatement: SqlPollStatement = relaxedMockk {
        every { query } returns ""
        every { parameters } returns emptyList()
    }

    val connectionPool: ConnectionPool = relaxedMockk()

    val dialect: Dialect = relaxedMockk()

    val connectionPoolFactory: () -> ConnectionPool = {
        connectionPool
    }

    @Test
    @Timeout(10)
    internal fun `should have no next when not running`() = testDispatcherProvider.run {
        // given
        val reader = spyk(
            SqlIterativeReader(
                this,
                connectionPoolFactory,
                dialect,
                sqlPollStatement,
                Duration.ofMillis(50),
                { Channel(1) }
            ), recordPrivateCalls = true
        )
        coJustRun { reader["poll"]() }


        // when + then
        Assertions.assertFalse(reader.hasNext())
        delay(200)
        coVerifyNever { reader["poll"]() }
    }

    @Test
    @Timeout(10)
    internal fun `should have next when running and poll`() = testDispatcherProvider.run {
        // given
        val countDownLatch = SuspendedCountLatch(3, true)
        val reader = spyk(
            SqlIterativeReader(
                this,
                connectionPoolFactory,
                dialect,
                sqlPollStatement,
                Duration.ofMillis(50),
                { Channel(1) }
            ), recordPrivateCalls = true
        )
        coEvery { reader["poll"]() } coAnswers { countDownLatch.decrement() }


        // when
        reader.start(relaxedMockk())

        // then
        Assertions.assertTrue(reader.hasNext())
        countDownLatch.await()
        coVerify(atLeast = 3) {
            reader["poll"]()
        }
    }

    @Test
    @Timeout(10)
    internal fun `should be stoppable`() = testDispatcherProvider.run {
        // given
        val countDownLatch = SuspendedCountLatch(3, true)
        val reader = spyk(
            SqlIterativeReader(
                this,
                connectionPoolFactory,
                dialect,
                sqlPollStatement,
                Duration.ofMillis(50),
                { Channel(1) }
            ), recordPrivateCalls = true
        )
        coEvery { reader["poll"]() } coAnswers { countDownLatch.decrement() }


        // when
        reader.start(relaxedMockk())

        // then
        countDownLatch.await()
        coVerify(atLeast = 3) {
            reader["poll"]()
        }
        clearMocks(reader, answers = false)

        // when
        reader.stop(relaxedMockk())

        // then
        Assertions.assertFalse(reader.hasNext())
        delay(200)
        coVerifyNever { reader["poll"]() }
    }

    @Test
    @Timeout(10)
    internal fun `should be restartable`() = testDispatcherProvider.run {
        // given
        // Count down for the first period of activity.
        val countDownLatch1 = SuspendedCountLatch(3)
        // Count down for the second period of activity.
        val countDownLatch2 = SuspendedCountLatch(3, true)
        val reader = spyk(
            SqlIterativeReader(
                this,
                connectionPoolFactory,
                dialect,
                sqlPollStatement,
                Duration.ofMillis(50),
                { Channel(1) }
            ), recordPrivateCalls = true
        )
        coEvery { reader["poll"]() } coAnswers {
            if (countDownLatch1.get() > 0) {
                countDownLatch1.decrement()
            } else {
                countDownLatch2.decrement()
            }
        }


        // when
        reader.start(relaxedMockk())

        // then
        countDownLatch1.await()
        coVerify(atLeast = 3) {
            reader["poll"]()
        }
        clearMocks(reader, answers = false)

        // when
        reader.stop(relaxedMockk())

        // then
        Assertions.assertFalse(reader.hasNext())
        delay(200)
        coVerifyNever { reader["poll"]() }

        // when
        reader.start(relaxedMockk())

        // then
        countDownLatch2.await()
        coVerify(atLeast = 3) {
            reader["poll"]()
        }
    }
}
