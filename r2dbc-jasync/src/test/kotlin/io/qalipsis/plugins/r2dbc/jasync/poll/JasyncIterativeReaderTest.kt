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

package io.qalipsis.plugins.r2dbc.jasync.poll

import com.github.jasync.sql.db.SuspendingConnection
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.every
import io.mockk.spyk
import io.qalipsis.api.sync.SuspendedCountLatch
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.CleanMockkRecordedCalls
import io.qalipsis.test.mockk.coVerifyNever
import io.qalipsis.test.mockk.relaxedMockk
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.Duration

/**
 *
 * @author Eric Jessé
 */
@Suppress("EXPERIMENTAL_API_USAGE")
@CleanMockkRecordedCalls
internal class JasyncIterativeReaderTest {

    @JvmField
    @RegisterExtension
    val testDispatcherProvider = TestDispatcherProvider()

    val sqlPollStatement: SqlPollStatement = relaxedMockk {
        every { query } returns ""
        every { parameters } returns emptyList()
    }

    val connection: SuspendingConnection = relaxedMockk()

    val connectionPoolFactory: () -> SuspendingConnection = {
        connection
    }

    @Test
    @Timeout(10)
    internal fun `should have no next when not running`() = testDispatcherProvider.run {
        // given
        val reader = spyk(
            JasyncIterativeReader(
                this,
                connectionPoolFactory,
                sqlPollStatement,
                Duration.ofMillis(50),
                { Channel(1) }
            ), recordPrivateCalls = true
        ) 
        coJustRun { reader["poll"](any<SuspendingConnection>()) }
        

        // when + then
        Assertions.assertFalse(reader.hasNext())
        delay(200)
        coVerifyNever { reader["poll"](any<SuspendingConnection>()) }
    }

    @Test
    @Timeout(10)
    internal fun `should have next when running and poll`() = testDispatcherProvider.run {
        // given
        val countDownLatch = SuspendedCountLatch(3, true)
        val reader = spyk(
            JasyncIterativeReader(
                this,
                connectionPoolFactory,
                sqlPollStatement,
                Duration.ofMillis(50),
                { Channel(1) }
            ), recordPrivateCalls = true
        )
        coEvery { reader["poll"](any<SuspendingConnection>()) } coAnswers { countDownLatch.decrement() }


        // when
        reader.start(relaxedMockk())

        // then
        Assertions.assertTrue(reader.hasNext())
        countDownLatch.await()
        coVerify(atLeast = 3) {
            reader["poll"](refEq(connection))
        }
    }

    @Test
    @Timeout(10)
    internal fun `should be stoppable`() = testDispatcherProvider.run {
        // given
        val countDownLatch = SuspendedCountLatch(3, true)
        val reader = spyk(
            JasyncIterativeReader(
                this,
                connectionPoolFactory,
                sqlPollStatement,
                Duration.ofMillis(50),
                { Channel(1) }
            ), recordPrivateCalls = true
        )
        coEvery { reader["poll"](any<SuspendingConnection>()) } coAnswers { countDownLatch.decrement() }


        // when
        reader.start(relaxedMockk())

        // then
        countDownLatch.await()
        coVerify(atLeast = 3) {
            reader["poll"](refEq(connection))
        }
        clearMocks(reader, answers = false)

        // when
        reader.stop(relaxedMockk())

        // then
        Assertions.assertFalse(reader.hasNext())
        delay(200)
        coVerifyNever { reader["poll"](any<SuspendingConnection>()) }
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
            JasyncIterativeReader(
                this,
                connectionPoolFactory,
                sqlPollStatement,
                Duration.ofMillis(50),
                { Channel(1) }
            ), recordPrivateCalls = true
        )
        coEvery { reader["poll"](any<SuspendingConnection>()) } coAnswers {
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
            reader["poll"](refEq(connection))
        }
        clearMocks(reader, answers = false)

        // when
        reader.stop(relaxedMockk())

        // then
        Assertions.assertFalse(reader.hasNext())
        delay(200)
        coVerifyNever { reader["poll"](any<SuspendingConnection>()) }

        // when
        reader.start(relaxedMockk())

        // then
        countDownLatch2.await()
        coVerify(atLeast = 3) {
            reader["poll"](refEq(connection))
        }
    }
}
