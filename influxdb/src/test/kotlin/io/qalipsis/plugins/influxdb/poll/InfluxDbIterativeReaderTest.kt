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

package io.qalipsis.plugins.influxdb.poll

import assertk.assertThat
import assertk.assertions.isFalse
import assertk.assertions.isNotNull
import assertk.assertions.isNotSameAs
import assertk.assertions.isNull
import assertk.assertions.isSameAs
import assertk.assertions.isTrue
import com.influxdb.client.kotlin.InfluxDBClientKotlin
import io.aerisconsulting.catadioptre.getProperty
import io.micrometer.core.instrument.MeterRegistry
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.impl.annotations.SpyK
import io.mockk.spyk
import io.mockk.verify
import io.qalipsis.api.events.EventsLogger
import io.qalipsis.api.sync.SuspendedCountLatch
import io.qalipsis.plugins.influxdb.InfluxDbStepConnectionImpl
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.relaxedMockk
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.Duration

@WithMockk
internal class InfluxDbIterativeReaderTest {

    @JvmField
    @RegisterExtension
    val testDispatcherProvider = TestDispatcherProvider()

    @RelaxedMockK
    private lateinit var resultsChannel: Channel<InfluxDbQueryResult>

    @SpyK
    private var resultsChannelFactory: () -> Channel<InfluxDbQueryResult> = { resultsChannel }

    @RelaxedMockK
    private lateinit var pollStatement: InfluxDbPollStatement

    @RelaxedMockK
    private lateinit var eventsLogger: EventsLogger

    @RelaxedMockK
    private lateinit var meterRegistry: MeterRegistry

    @RelaxedMockK
    private lateinit var client: InfluxDBClientKotlin

    private val clientBuilder: () -> InfluxDBClientKotlin by lazy { { client } }

    @Test
    @Timeout(3)
    internal fun `should be restartable`() = testDispatcherProvider.run {
        val connectionConfig = InfluxDbStepConnectionImpl()

        connectionConfig.password = "pass"
        connectionConfig.url = "http://127.0.0.1:8086"
        connectionConfig.user = "name"
        connectionConfig.bucket = "db"
        // given
        val latch = SuspendedCountLatch(1, true)
        val reader = spyk(
            InfluxDbIterativeReader(
                clientFactory = clientBuilder,
                pollStatement = pollStatement,
                pollDelay = Duration.ofMillis(300),
                resultsChannelFactory = resultsChannelFactory,
                coroutineScope = this,
                eventsLogger = eventsLogger,
                meterRegistry = meterRegistry,
            ), recordPrivateCalls = true
        )
        coEvery { reader["poll"](any<InfluxDBClientKotlin>()) } coAnswers { latch.decrement() }

        // when
        reader.start(relaxedMockk { })

        // then
        latch.await()
        verify { reader["init"]() }
        verify { resultsChannelFactory() }
        assertThat(reader.hasNext()).isTrue()
        val pollingJob = reader.getProperty<Job>("pollingJob")
        assertThat(pollingJob).isNotNull()
        val client = reader.getProperty<InfluxDBClientKotlin>("client")
        assertThat(client).isNotNull()
        val resultsChannel = reader.getProperty<Channel<InfluxDbQueryResult>>("resultsChannel")
        assertThat(resultsChannel).isSameAs(resultsChannel)

        // when
        reader.stop(relaxedMockk())
        verify { resultsChannel.cancel() }
        verify { pollStatement.reset() }
        // then
        assertThat(reader.hasNext()).isFalse()

        // when
        latch.reset()
        reader.start(relaxedMockk { })
        // then
        verify { reader["init"]() }
        verify { resultsChannelFactory() }
        assertThat(reader.hasNext()).isTrue()
        assertThat(reader.getProperty<Job>("pollingJob")).isNotSameAs(pollingJob)
        assertThat(reader.getProperty<InfluxDBClientKotlin>("client")).isNotNull()
        assertThat(reader.getProperty<Channel<InfluxDbQueryResult>>("resultsChannel")).isSameAs(resultsChannel)

        reader.stop(relaxedMockk())
    }

    @Test
    @Timeout(10)
    fun `should be empty before start`() = testDispatcherProvider.run {
        val connectionConfig = InfluxDbStepConnectionImpl()

        connectionConfig.password = "pass"
        connectionConfig.url = "http://127.0.0.1:8086"
        connectionConfig.user = "name"
        connectionConfig.bucket = "db"
        // given
        val reader = spyk(
            InfluxDbIterativeReader(
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
        assertThat(reader.getProperty<Channel<InfluxDbQueryResult>>("resultsChannel")).isNull()
    }

    @Test
    @Timeout(20)
    fun `should poll at least twice after start`() = testDispatcherProvider.run {
        // given
        val connectionConfig = InfluxDbStepConnectionImpl()

        connectionConfig.password = "pass"
        connectionConfig.url = "http://127.0.0.1:8086"
        connectionConfig.user = "name"
        connectionConfig.bucket = "db"
        val reader = spyk(
            InfluxDbIterativeReader(
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
        coEvery { reader["poll"](any<InfluxDBClientKotlin>()) } coAnswers { countDownLatch.decrement() }

        // when
        reader.start(relaxedMockk())
        countDownLatch.await()

        // then
        coVerify(atLeast = 2) { reader["poll"](any<InfluxDBClientKotlin>()) }
        assertThat(reader.hasNext()).isTrue()

        reader.stop(relaxedMockk())
    }
}
