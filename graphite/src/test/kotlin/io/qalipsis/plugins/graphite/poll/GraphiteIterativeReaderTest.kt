package io.qalipsis.plugins.graphite.poll

import assertk.assertThat
import assertk.assertions.isFalse
import assertk.assertions.isNotNull
import assertk.assertions.isNotSameAs
import assertk.assertions.isNull
import assertk.assertions.isSameAs
import assertk.assertions.isTrue
import io.aerisconsulting.catadioptre.getProperty
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.impl.annotations.SpyK
import io.mockk.spyk
import io.mockk.verify
import io.qalipsis.api.context.StepStartStopContext
import io.qalipsis.api.events.EventsLogger
import io.qalipsis.api.meters.CampaignMeterRegistry
import io.qalipsis.api.meters.Counter
import io.qalipsis.api.sync.SuspendedCountLatch
import io.qalipsis.plugins.graphite.search.GraphiteRenderApiService
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.relaxedMockk
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.Duration

@WithMockk
internal class GraphiteIterativeReaderTest {

    @JvmField
    @RegisterExtension
    val testDispatcherProvider = TestDispatcherProvider()

    @RelaxedMockK
    private lateinit var resultsChannel: Channel<GraphiteQueryResult>

    @SpyK
    private var resultsChannelFactory: () -> Channel<GraphiteQueryResult> = { resultsChannel }

    @RelaxedMockK
    private lateinit var pollStatement: GraphitePollStatement

    @RelaxedMockK
    private lateinit var eventsLogger: EventsLogger

    @RelaxedMockK
    private lateinit var meterRegistry: CampaignMeterRegistry

    @RelaxedMockK
    private lateinit var client: GraphiteRenderApiService

    private val clientBuilder: () -> GraphiteRenderApiService by lazy { { client } }

    private val recordsCount = relaxedMockk<Counter>()

    private val failureCounter = relaxedMockk<Counter>()

    @Test
    @Timeout(6)
    fun `should be restartable`() = testDispatcherProvider.run {
        // given
        val latch = SuspendedCountLatch(1, true)

        val tags: Map<String, String> = emptyMap()
        every {
            meterRegistry.counter(
                "scenario-test",
                "step-test",
                "graphite-poll-received-records",
                refEq(tags)
            )
        } returns recordsCount
        every { recordsCount.report(any()) } returns recordsCount
        every {
            meterRegistry.counter(
                "scenario-test",
                "step-test",
                "graphite-poll-failures",
                refEq(tags)
            )
        } returns failureCounter
        every { failureCounter.report(any()) } returns failureCounter

        val startStopContext = relaxedMockk<StepStartStopContext> {
            every { toEventTags() } returns tags
            every { scenarioName } returns "scenario-test"
            every { stepName } returns "step-test"
        }
        val reader = spyk(
            GraphiteIterativeReader(
                clientFactory = clientBuilder,
                pollStatement = pollStatement,
                pollDelay = Duration.ofMillis(300),
                resultsChannelFactory = resultsChannelFactory,
                coroutineScope = this,
                eventsLogger = eventsLogger,
                meterRegistry = meterRegistry,
            ), recordPrivateCalls = true
        )
        coEvery { reader["poll"]() } coAnswers { latch.decrement() }

        // when
        reader.start(startStopContext)

        // then
        latch.await()
        verify { reader["init"]() }
        verify { resultsChannelFactory() }
        assertThat(reader.hasNext()).isTrue()
        val pollingJob = reader.getProperty<Job>("pollingJob")
        assertThat(pollingJob).isNotNull()
        val client = reader.getProperty<GraphiteRenderApiService>("client")
        assertThat(client).isNotNull()
        val resultsChannel = reader.getProperty<Channel<GraphiteQueryResult>>("resultsChannel")
        assertThat(resultsChannel).isSameAs(resultsChannel)

        // when
        reader.stop(relaxedMockk())

        // then
        verify { resultsChannel.cancel() }
        verify { pollStatement.reset() }
        assertThat(reader.hasNext()).isFalse()

        // when
        latch.reset()
        reader.start(startStopContext)
        // then
        verify { reader["init"]() }
        verify { resultsChannelFactory() }
        assertThat(reader.hasNext()).isTrue()
        assertThat(reader.getProperty<Job>("pollingJob")).isNotSameAs(pollingJob)
        assertThat(reader.getProperty<GraphiteRenderApiService>("client")).isNotNull()
        assertThat(reader.getProperty<Channel<GraphiteQueryResult>>("resultsChannel")).isSameAs(resultsChannel)

        reader.stop(startStopContext)
    }

    @Test
    @Timeout(10)
    fun `should be empty before start`() = testDispatcherProvider.run {

        // given
        val reader = spyk(
            GraphiteIterativeReader(
                clientFactory = clientBuilder,
                pollStatement = pollStatement,
                pollDelay = Duration.ofMillis(300),
                resultsChannelFactory = resultsChannelFactory,
                coroutineScope = this,
                eventsLogger = eventsLogger,
                meterRegistry = meterRegistry
            ), recordPrivateCalls = true
        )

        // then
        assertThat(reader.hasNext()).isFalse()
        assertThat(reader.getProperty<Channel<GraphiteQueryResult>>("resultsChannel")).isNull()
    }

    @Test
    @Timeout(20)
    fun `should poll at least twice after start`() = runBlocking {
        // given
        val tags: Map<String, String> = emptyMap()
        every {
            meterRegistry.counter(
                "scenario-test",
                "step-test",
                "graphite-poll-received-records",
                refEq(tags)
            )
        } returns recordsCount
        every { recordsCount.report(any()) } returns recordsCount
        every {
            meterRegistry.counter(
                "scenario-test",
                "step-test",
                "graphite-poll-failures",
                refEq(tags)
            )
        } returns failureCounter
        every { failureCounter.report(any()) } returns failureCounter

        val startStopContext = relaxedMockk<StepStartStopContext> {
            every { toEventTags() } returns tags
            every { scenarioName } returns "scenario-test"
            every { stepName } returns "step-test"
        }
        val reader = spyk(
            GraphiteIterativeReader(
                clientFactory = clientBuilder,
                pollStatement = pollStatement,
                pollDelay = Duration.ofMillis(300),
                resultsChannelFactory = resultsChannelFactory,
                coroutineScope = this,
                eventsLogger = eventsLogger,
                meterRegistry = meterRegistry,
            ), recordPrivateCalls = true
        )
        val countDownLatch = SuspendedCountLatch(2, true)
        coEvery { reader["poll"]() } coAnswers { countDownLatch.decrement() }

        // when
        reader.start(startStopContext)
        countDownLatch.await()

        // then
        coVerify(atLeast = 2) { reader["poll"]() }
        assertThat(reader.hasNext()).isTrue()

        reader.stop(startStopContext)
    }

}