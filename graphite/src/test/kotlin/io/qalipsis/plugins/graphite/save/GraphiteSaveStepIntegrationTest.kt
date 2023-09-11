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

package io.qalipsis.plugins.graphite.save

import assertk.all
import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.index
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.startsWith
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.micronaut.http.HttpStatus
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.verify
import io.netty.channel.nio.NioEventLoopGroup
import io.qalipsis.api.context.StepStartStopContext
import io.qalipsis.api.events.EventsLogger
import io.qalipsis.api.meters.CampaignMeterRegistry
import io.qalipsis.api.meters.Counter
import io.qalipsis.api.meters.Meter
import io.qalipsis.api.meters.Timer
import io.qalipsis.plugins.graphite.Constants
import io.qalipsis.test.assertk.prop
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.relaxedMockk
import kotlinx.coroutines.delay
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.extension.RegisterExtension
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.HostPortWaitStrategy
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.shaded.org.awaitility.Awaitility.await
import org.testcontainers.utility.DockerImageName
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeUnit
import io.ktor.client.HttpClient as KtorHttpClient

/**
 *
 * @author Palina Bril
 */
@Testcontainers
@WithMockk
internal class GraphiteSaveStepIntegrationTest {

    @JvmField
    @RegisterExtension
    val testDispatcherProvider = TestDispatcherProvider()

    private val container = CONTAINER

    private var httpPort = -1

    private var graphiteProtocolPort = -1

    private val ktorHttpClient = KtorHttpClient(CIO)

    private val httpClient = createSyncHttpClient()

    @RelaxedMockK
    private lateinit var timeToResponse: Timer

    @RelaxedMockK
    private lateinit var recordsCount: Counter

    @RelaxedMockK
    private lateinit var successCounter: Counter

    @RelaxedMockK
    private lateinit var eventsLogger: EventsLogger

    @BeforeEach
    fun setUp() = testDispatcherProvider.run {
        httpPort = container.getMappedPort(Constants.HTTP_PORT)
        val request = "http://localhost:${httpPort}/render"
        while (ktorHttpClient.get(request).status.value != HttpStatus.OK.code) {
            delay(1_000)
        }
        graphiteProtocolPort = container.getMappedPort(Constants.GRAPHITE_PLAINTEXT_PORT)
    }

    @Test
    @Timeout(50)
    fun `should succeed when sending query with results`() = testDispatcherProvider.run {
        //given
        val eventTags = emptyMap<String, String>()
        val meterRegistry = relaxedMockk<CampaignMeterRegistry> {
            every {
                counter(
                    "scenario-test",
                    "step-test",
                    "graphite-save-saving-messages",
                    refEq(eventTags)
                )
            } returns recordsCount
            every { recordsCount.report(any()) } returns recordsCount
            every {
                counter(
                    "scenario-test",
                    "step-test",
                    "graphite-save-successes",
                    refEq(eventTags)
                )
            } returns successCounter
            every { successCounter.report(any()) } returns successCounter
            every {
                timer(
                    "scenario-test",
                    "step-test",
                    "graphite-save-time-to-response",
                    refEq(eventTags)
                )
            } returns timeToResponse
        }
        val startStopContext = relaxedMockk<StepStartStopContext> {
            every { toEventTags() } returns eventTags
            every { scenarioName } returns "scenario-test"
            every { stepName } returns "step-test"
        }
        val results = mutableListOf<String>()
        val saveClient = GraphiteSaveMessageClientImpl(
            clientBuilder = {
                GraphiteSaveClient(
                    host = "localhost",
                    port = graphiteProtocolPort,
                    workerGroup = NioEventLoopGroup(),
                    coroutineScope = this
                )
            },
            meterRegistry = meterRegistry,
            eventsLogger = eventsLogger
        )
        val tags: Map<String, String> = emptyMap()

        saveClient.start(startStopContext)

        val key = "foo.first"
        val keyTwo = "foo.second"
        val keyThird = "foo.third"

        val request = generateHttpGet("http://localhost:${httpPort}/render?target=$key&format=json")
        val strRequest = "http://localhost:${httpPort}/render?target=$key&format=json"
        val strRequestTwo = "http://localhost:${httpPort}/render?target=$keyTwo&format=json"
        val strRequestThree = "http://localhost:${httpPort}/render?target=$keyThird&format=json"

        val now = Instant.now()

        // when
        val resultOfExecute = saveClient.execute(
            listOf(
                GraphiteRecord("foo.first", 1.1, now),
                GraphiteRecord("foo.second", 1.2, now),
                GraphiteRecord("foo.third", 1.3, now)
            ), tags
        )

        // then
        assertThat(resultOfExecute).isInstanceOf(GraphiteSaveQueryMeters::class.java).all {
            prop("savedMessages").isEqualTo(3)
            prop("timeToResult").isNotNull()
        }
        verify {
            eventsLogger.debug("graphite.save.saving-messages", 3, any(), tags = tags)
            timeToResponse.record(more(0L), TimeUnit.NANOSECONDS)
            recordsCount.increment(3.0)
            recordsCount.report(any<Meter.ReportingConfiguration<Counter>.() -> Unit>())
            eventsLogger.info("graphite.save.time-to-response", any<Duration>(), any(), tags = tags)
            eventsLogger.info("graphite.save.successes", any<Array<*>>(), any(), tags = tags)
        }
        confirmVerified(timeToResponse, recordsCount, eventsLogger)
        await().atMost(10, TimeUnit.SECONDS)
            .until { httpClient.send(request, HttpResponse.BodyHandlers.ofString()).body().contains(key) }
        results.add(ktorHttpClient.get(strRequest).body())
        results.add(ktorHttpClient.get(strRequestTwo).body())
        results.add(ktorHttpClient.get(strRequestThree).body())
        assertThat(results).all {
            hasSize(3)
            index(0).all {
                startsWith("[{\"target\": \"foo.first\", \"tags\": {\"name\": \"foo.first\"},")
            }
            index(1).all {
                startsWith("[{\"target\": \"foo.second\", \"tags\": {\"name\": \"foo.second\"},")
            }
            index(2).all {
                startsWith("[{\"target\": \"foo.third\", \"tags\": {\"name\": \"foo.third\"},")
            }
        }
        saveClient.stop(startStopContext)
    }

    @Test
    @Timeout(10)
    fun `should fail when sending wrong format message`() = testDispatcherProvider.run {
        //given
        val eventTags = emptyMap<String, String>()
        val meterRegistry = relaxedMockk<CampaignMeterRegistry> {
            every {
                counter(
                    "scenario-test",
                    "step-test",
                    "graphite-save-saving-messages",
                    refEq(eventTags)
                )
            } returns recordsCount
            every { recordsCount.report(any()) } returns recordsCount
            every {
                timer(
                    "scenario-test",
                    "step-test",
                    "graphite-save-time-to-response",
                    refEq(eventTags)
                )
            } returns timeToResponse
            every {
                counter(
                    "scenario-test",
                    "step-test",
                    "graphite-save-successes",
                    refEq(eventTags)
                )
            } returns successCounter
            every { successCounter.report(any()) } returns successCounter
        }
        val startStopContext = relaxedMockk<StepStartStopContext> {
            every { toEventTags() } returns eventTags
            every { scenarioName } returns "scenario-test"
            every { stepName } returns "step-test"
        }
        val results = mutableListOf<String>()
        val saveClient = GraphiteSaveMessageClientImpl(
            clientBuilder = {
                GraphiteSaveClient(
                    host = "localhost",
                    port = graphiteProtocolPort,
                    workerGroup = NioEventLoopGroup(),
                    coroutineScope = this
                )
            },
            meterRegistry = meterRegistry,
            eventsLogger = eventsLogger
        )
        saveClient.start(startStopContext)
        val tags: Map<String, String> = emptyMap()

        // when
        // No exception can be returned since there is no response from the server to know whether the records were saved or not.
        saveClient.execute(listOf(
            GraphiteRecord("hola.first", 1.1),
            GraphiteRecord("hola.second", 1.2)), tags)

        //then
        val key = "hola.first"
        val request = "http://localhost:${httpPort}/render?target=$key&format=json"
        results.add(ktorHttpClient.get(request).body())
        assertThat(results).all {
            hasSize(1)
            index(0).all {
                isEqualTo("[]")
            }
        }
        saveClient.stop(startStopContext)
    }

    private fun generateHttpGet(uri: String): HttpRequest =
        HttpRequest.newBuilder()
            .GET()
            .uri(URI.create(uri))
            .build()

    private fun createSyncHttpClient(): HttpClient =
        HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .build()

    companion object {

        @Container
        @JvmStatic
        private val CONTAINER = GenericContainer<Nothing>(
            DockerImageName.parse(Constants.GRAPHITE_IMAGE_NAME)
        ).apply {
            setWaitStrategy(HostPortWaitStrategy())
            withExposedPorts(Constants.HTTP_PORT, Constants.GRAPHITE_PLAINTEXT_PORT)
            withAccessToHost(true)
            withStartupTimeout(Duration.ofSeconds(60))
            withCreateContainerCmdModifier { it.hostConfig!!.withMemory((512 * 1e20).toLong()).withCpuCount(2) }
        }
    }
}