package io.qalipsis.plugins.redis.lettuce.poll

import io.lettuce.core.api.StatefulConnection
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.spyk
import io.qalipsis.api.context.StepStartStopContext
import io.qalipsis.api.sync.SuspendedCountLatch
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.WithMockk
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
 * @author Gabriel Moraes
 */
@WithMockk
internal class LettuceIterativeReaderTest {

    private val scanner: LettuceScanner = relaxedMockk()

    private val connection: StatefulConnection<ByteArray, ByteArray> = relaxedMockk()

    private val connectionFactory: suspend () -> StatefulConnection<ByteArray, ByteArray> = suspend {
        connection
    }

    @JvmField
    @RegisterExtension
    val testDispatcherProvider = TestDispatcherProvider()

    @Test
    @Timeout(10)
    internal fun `should have no next when not running`() = testDispatcherProvider.run {
        // given
        val reader = spyk(
            LettuceIterativeReader(
                this,
                connectionFactory,
                Duration.ofSeconds(1),
                "test",
                scanner
            ) { Channel(1) }, recordPrivateCalls = true
        ) 
        
        coJustRun { reader["poll"](any<StatefulConnection<ByteArray, ByteArray>>(), any<StepStartStopContext>()) }
        

        // when + then
        Assertions.assertFalse(reader.hasNext())
        delay(200)
        coVerifyNever { reader["poll"](any<StatefulConnection<ByteArray, ByteArray>>(), any<StepStartStopContext>()) }
    }

    @Test
    @Timeout(10)
    internal fun `should have next when running and poll`() = testDispatcherProvider.run {
        // given
        val countDownLatch = SuspendedCountLatch(3, true)
        val reader = spyk(
            LettuceIterativeReader(
                this,
                connectionFactory,
                Duration.ofSeconds(1),
                "test",
                scanner
            ) { Channel(1) }, recordPrivateCalls = true
        ) 
        coEvery { reader["poll"](any<StatefulConnection<ByteArray, ByteArray>>(), any<StepStartStopContext>()) } coAnswers { countDownLatch.decrement() }
        

        // when
        reader.start(relaxedMockk())

        // then
        Assertions.assertTrue(reader.hasNext())
        countDownLatch.await()
        coVerify(atLeast = 3) {
            reader["poll"](any<StatefulConnection<ByteArray, ByteArray>>(), any<StepStartStopContext>())
        }
    }

    @Test
    @Timeout(10)
    internal fun `should be stoppable`() = testDispatcherProvider.run {
        // given
        val countDownLatch = SuspendedCountLatch(3, true)
        val reader = spyk(
            LettuceIterativeReader(
                this,
                connectionFactory,
                Duration.ofSeconds(1),
                "test",
                scanner
            ) { Channel(1) }, recordPrivateCalls = true
        )
        coEvery { reader["poll"](any<StatefulConnection<ByteArray, ByteArray>>(), any<StepStartStopContext>()) } coAnswers { countDownLatch.decrement() }


        // when
        reader.start(relaxedMockk())

        // then
        countDownLatch.await()
        coVerify(atLeast = 3) {
            reader["poll"](any<StatefulConnection<ByteArray, ByteArray>>(), any<StepStartStopContext>())
        }
        clearMocks(reader, answers = false)

        // when
        reader.stop(relaxedMockk())

        // then
        Assertions.assertFalse(reader.hasNext())
        delay(200)
        coVerifyNever { reader["poll"](any<StatefulConnection<ByteArray, ByteArray>>(), any<StepStartStopContext>()) }
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
            LettuceIterativeReader(
                this,
                connectionFactory,
                Duration.ofSeconds(1),
                "test",
                scanner
            ) { Channel(1) }, recordPrivateCalls = true
        )
        coEvery { reader["poll"](any<StatefulConnection<ByteArray, ByteArray>>(), any<StepStartStopContext>()) } coAnswers {
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
            reader["poll"](any<StatefulConnection<ByteArray, ByteArray>>(), any<StepStartStopContext>())
        }
        clearMocks(reader, answers = false)

        // when
        reader.stop(relaxedMockk())

        // then
        Assertions.assertFalse(reader.hasNext())
        delay(200)
        coVerifyNever { reader["poll"](any<StatefulConnection<ByteArray, ByteArray>>(), any<StepStartStopContext>()) }

        // when
        reader.start(relaxedMockk())

        // then
        countDownLatch2.await()
        coVerify(atLeast = 3) {
            reader["poll"](any<StatefulConnection<ByteArray, ByteArray>>(), any<StepStartStopContext>())
        }
    }
}
