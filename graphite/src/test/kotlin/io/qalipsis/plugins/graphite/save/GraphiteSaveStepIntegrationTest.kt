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

@file:OptIn(ExperimentalCoroutinesApi::class)

package io.qalipsis.plugins.graphite.save

import assertk.all
import assertk.assertThat
import assertk.assertions.any
import assertk.assertions.containsAll
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.isSameAs
import assertk.assertions.isTrue
import assertk.assertions.prop
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinFeature
import com.fasterxml.jackson.module.kotlin.jsonMapper
import com.fasterxml.jackson.module.kotlin.kotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.slot
import io.mockk.verify
import io.netty.channel.ChannelOutboundHandlerAdapter
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.context.StepStartStopContext
import io.qalipsis.api.events.EventsLogger
import io.qalipsis.api.meters.CampaignMeterRegistry
import io.qalipsis.api.meters.Counter
import io.qalipsis.api.meters.Meter
import io.qalipsis.api.meters.Timer
import io.qalipsis.plugins.graphite.Constants
import io.qalipsis.plugins.graphite.client.GraphiteRecord
import io.qalipsis.plugins.graphite.client.GraphiteTcpClient
import io.qalipsis.plugins.graphite.client.codecs.PickleEncoder
import io.qalipsis.plugins.graphite.client.codecs.PlaintextEncoder
import io.qalipsis.plugins.graphite.search.DataPoints
import io.qalipsis.test.assertk.prop
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.relaxedMockk
import io.qalipsis.test.steps.StepTestHelper
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.ReceiveChannel
import org.apache.commons.lang3.RandomStringUtils
import org.awaitility.kotlin.await
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.RegisterExtension
import org.testcontainers.containers.BindMode
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.HttpWaitStrategy
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.channels.ClosedChannelException
import java.time.Clock
import java.time.Duration
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 *
 * @author Palina Bril
 */
@Testcontainers
@WithMockk
internal class GraphiteSaveStepIntegrationTest {

    private val httpClient = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build()

    private val objectMapper = jsonMapper().registerModules(kotlinModule {
        configure(KotlinFeature.NullToEmptyCollection, true)
        configure(KotlinFeature.NullToEmptyMap, true)
        configure(KotlinFeature.NullIsSameAsDefault, true)
    }, JavaTimeModule())

    @RelaxedMockK
    private lateinit var timeToResponse: Timer

    @RelaxedMockK
    private lateinit var recordsCount: Counter

    @RelaxedMockK
    private lateinit var successCounter: Counter

    @RelaxedMockK
    private lateinit var eventsLogger: EventsLogger

    @RelaxedMockK
    private lateinit var campaignMeterRegistry: CampaignMeterRegistry

    abstract inner class ProtocolTest {

        @JvmField
        @RegisterExtension
        val testDispatcherProvider = TestDispatcherProvider()

        abstract val protocolPathIdentifier: String

        abstract val recordEncoder: ChannelOutboundHandlerAdapter

        abstract val graphitePort: Int

        private var httpPort: Int = -1

        private var actualGraphitePort: Int = -1

        @BeforeAll
        fun prepare() {
            httpPort = CONTAINER.getMappedPort(Constants.HTTP_PORT)
            actualGraphitePort = CONTAINER.getMappedPort(graphitePort)
        }

        @Test
        @Timeout(10)
        fun `should send records with or without tags when meters and event loggers are enabled`() =
            testDispatcherProvider.run {
                //given
                val metersTags = emptyMap<String, String>()
                every {
                    campaignMeterRegistry.counter(
                        "scenario-test",
                        "step-test",
                        "graphite-save-saving-messages",
                        refEq(metersTags)
                    )
                } returns recordsCount
                every { recordsCount.report(any()) } returns recordsCount
                every {
                    campaignMeterRegistry.counter(
                        "scenario-test",
                        "step-test",
                        "graphite-save-successes",
                        refEq(metersTags)
                    )
                } returns successCounter
                every { successCounter.report(any()) } returns successCounter
                every {
                    campaignMeterRegistry.timer(
                        "scenario-test",
                        "step-test",
                        "graphite-save-time-to-response",
                        refEq(metersTags)
                    )
                } returns timeToResponse
                val startStopContext = relaxedMockk<StepStartStopContext> {
                    every { toEventTags() } returns metersTags
                    every { scenarioName } returns "scenario-test"
                    every { stepName } returns "step-test"
                }
                val now = Clock.tickSeconds(ZoneId.systemDefault()).instant()
                val capturedInput = slot<String>()
                val capturedContext = slot<StepContext<*, *>>()
                val saveStep = GraphiteSaveStep<String>(
                    id = "",
                    retryPolicy = null,
                    clientBuilder = {
                        GraphiteTcpClient("localhost", actualGraphitePort, listOfNotNull(recordEncoder))
                    },
                    eventsLogger = eventsLogger,
                    meterRegistry = campaignMeterRegistry,
                    messageFactory = { ctx, input ->
                        capturedContext.captured = ctx
                        capturedInput.captured = input

                        listOf(
                            GraphiteRecord("qalipsis.$protocolPathIdentifier.foo.first", now, 1.1),
                            GraphiteRecord(
                                "qalipsis.$protocolPathIdentifier.foo.second",
                                now.minusSeconds(1),
                                -1.2,
                                tags = mapOf("tag1" to "value1", "tag2" to "value2")
                            ),
                            GraphiteRecord("qalipsis.$protocolPathIdentifier.foo.third", now.minusSeconds(2), 1.3),
                            GraphiteRecord("qalipsis.$protocolPathIdentifier.foo.third", now.minusSeconds(4), 1.4)
                        )
                    }
                )
                saveStep.start(startStopContext)
                val input = RandomStringUtils.randomAlphanumeric(10)
                val stepContext = StepTestHelper.createStepContext<String, GraphiteSaveResult<String>>(input = input)

                // when
                saveStep.execute(stepContext)
                saveStep.stop(startStopContext)

                // then
                val result =
                    (stepContext.output as ReceiveChannel<StepContext.StepOutputRecord<GraphiteSaveResult<String>>>).receive()
                assertThat(result).prop(StepContext.StepOutputRecord<GraphiteSaveResult<String>>::value).all {
                    prop(GraphiteSaveResult<*>::input).isEqualTo(input)
                    prop(GraphiteSaveResult<*>::meters).all {
                        prop("savedMessages").isEqualTo(4)
                        prop("timeToResult").isNotNull()
                    }
                }
                assertThat(capturedContext.captured).isSameAs(stepContext)
                assertThat(capturedInput.captured).isEqualTo(input)

                // Wait until the tags are properly created, since the operation is asynchronous.
                await.atMost(10, TimeUnit.SECONDS).until {
                    val savedTagsResponse = httpClient.send(
                        HttpRequest.newBuilder()
                            .GET()
                            .uri(URI.create("http://localhost:$httpPort/tags/findSeries?pretty=1&expr=name=~qalipsis.${protocolPathIdentifier}.foo.*"))
                            .build(), HttpResponse.BodyHandlers.ofString()
                    )
                    savedTagsResponse.body() != "[]"
                }

                val savedValuesResponse = httpClient.send(
                    HttpRequest.newBuilder()
                        .GET()
                        .uri(URI.create("http://localhost:$httpPort/render?target=qalipsis.${protocolPathIdentifier}.foo.**&target=seriesByTag('name=~qalipsis.${protocolPathIdentifier}.foo.*')&noNullPoints=True&format=json"))
                        .build(), HttpResponse.BodyHandlers.ofString()
                )
                val dataPoints = objectMapper.readValue<List<DataPoints>>(savedValuesResponse.body())
                assertThat(dataPoints).all {
                    hasSize(3)
                    any {
                        it.prop(DataPoints::target)
                            .isEqualTo("qalipsis.${protocolPathIdentifier}.foo.first")
                        it.prop(DataPoints::tags).isEqualTo(
                            mapOf("name" to "qalipsis.${protocolPathIdentifier}.foo.first")
                        )
                        it.prop(DataPoints::dataPoints).all {
                            hasSize(1)
                            containsAll(
                                DataPoints.DataPoint(1.1, now)
                            )
                        }
                    }
                    any {
                        it.prop(DataPoints::target)
                            .isEqualTo("qalipsis.${protocolPathIdentifier}.foo.second;tag1=value1;tag2=value2")
                        it.prop(DataPoints::tags)
                            .isEqualTo(
                                mapOf(
                                    "name" to "qalipsis.${protocolPathIdentifier}.foo.second",
                                    "tag1" to "value1",
                                    "tag2" to "value2"
                                )
                            )
                        it.prop(DataPoints::dataPoints).all {
                            hasSize(1)
                            containsAll(
                                DataPoints.DataPoint(-1.2, now.minusSeconds(1))
                            )
                        }
                    }
                    any {
                        it.prop(DataPoints::target)
                            .isEqualTo("qalipsis.${protocolPathIdentifier}.foo.third")
                        it.prop(DataPoints::tags).isEqualTo(
                            mapOf("name" to "qalipsis.${protocolPathIdentifier}.foo.third")
                        )
                        it.prop(DataPoints::dataPoints).all {
                            hasSize(2)
                            containsAll(
                                DataPoints.DataPoint(1.3, now.minusSeconds(2)),
                                DataPoints.DataPoint(1.4, now.minusSeconds(4))
                            )
                        }
                    }
                }

                verify {
                    eventsLogger.debug("graphite.save.saving-messages", 4, any(), any<Map<String, String>>())
                    timeToResponse.record(more(0L), TimeUnit.NANOSECONDS)
                    recordsCount.increment(4.0)
                    recordsCount.report(any<Meter.ReportingConfiguration<Counter>.() -> Unit>())
                    eventsLogger.info(
                        "graphite.save.time-to-response",
                        any<Duration>(),
                        any(),
                        any<Map<String, String>>()
                    )
                    eventsLogger.info("graphite.save.successes", any<Array<*>>(), any(), any<Map<String, String>>())
                }
                confirmVerified(timeToResponse, recordsCount, eventsLogger)
            }


        @Test
        @Timeout(10)
        fun `should send records with or without tags when meters and event loggers are disabled`() =
            testDispatcherProvider.run {
                //given
                val startStopContext = relaxedMockk<StepStartStopContext> {
                    every { toEventTags() } returns emptyMap()
                    every { scenarioName } returns "scenario-test"
                    every { stepName } returns "step-test"
                }
                val now = Clock.tickSeconds(ZoneId.systemDefault()).instant()
                val capturedInput = slot<String>()
                val capturedContext = slot<StepContext<*, *>>()
                val saveStep = GraphiteSaveStep<String>(
                    id = "",
                    retryPolicy = null,
                    clientBuilder = {
                        GraphiteTcpClient("localhost", actualGraphitePort, listOfNotNull(recordEncoder))
                    },
                    eventsLogger = null,
                    meterRegistry = null,
                    messageFactory = { ctx, input ->
                        capturedContext.captured = ctx
                        capturedInput.captured = input

                        listOf(
                            GraphiteRecord("qalipsis.$protocolPathIdentifier.bar.first", now, 1.1),
                            GraphiteRecord(
                                "qalipsis.$protocolPathIdentifier.bar.second",
                                now.minusSeconds(1),
                                -1.2,
                                tags = mapOf("tag1" to "value1", "tag2" to "value2")
                            ),
                            GraphiteRecord("qalipsis.$protocolPathIdentifier.bar.third", now.minusSeconds(2), 1.3),
                            GraphiteRecord("qalipsis.$protocolPathIdentifier.bar.third", now.minusSeconds(4), 1.4)
                        )
                    }
                )
                saveStep.start(startStopContext)
                val input = RandomStringUtils.randomAlphanumeric(10)
                val stepContext = StepTestHelper.createStepContext<String, GraphiteSaveResult<String>>(input = input)

                // when
                saveStep.execute(stepContext)
                saveStep.stop(startStopContext)

                // then
                val result =
                    (stepContext.output as ReceiveChannel<StepContext.StepOutputRecord<GraphiteSaveResult<String>>>).receive()
                assertThat(result).prop(StepContext.StepOutputRecord<GraphiteSaveResult<String>>::value).all {
                    prop(GraphiteSaveResult<*>::input).isEqualTo(input)
                    prop(GraphiteSaveResult<*>::meters).all {
                        prop("savedMessages").isEqualTo(4)
                        prop("timeToResult").isNotNull()
                    }
                }
                assertThat(capturedContext.captured).isSameAs(stepContext)
                assertThat(capturedInput.captured).isEqualTo(input)

                // Wait until the tags are properly created, since the operation is asynchronous.
                await.atMost(10, TimeUnit.SECONDS).until {
                    val savedTagsResponse = httpClient.send(
                        HttpRequest.newBuilder()
                            .GET()
                            .uri(URI.create("http://localhost:$httpPort/tags/findSeries?pretty=1&expr=name=~qalipsis.${protocolPathIdentifier}.bar.*"))
                            .build(), HttpResponse.BodyHandlers.ofString()
                    )
                    savedTagsResponse.body() != "[]"
                }

                val savedValuesResponse = httpClient.send(
                    HttpRequest.newBuilder()
                        .GET()
                        .uri(URI.create("http://localhost:$httpPort/render?target=qalipsis.${protocolPathIdentifier}.bar.**&target=seriesByTag('name=~qalipsis.${protocolPathIdentifier}.bar.*')&noNullPoints=True&format=json"))
                        .build(), HttpResponse.BodyHandlers.ofString()
                )
                val dataPoints = objectMapper.readValue<List<DataPoints>>(savedValuesResponse.body())
                assertThat(dataPoints).all {
                    hasSize(3)
                    any {
                        it.prop(DataPoints::target)
                            .isEqualTo("qalipsis.${protocolPathIdentifier}.bar.first")
                        it.prop(DataPoints::tags).isEqualTo(
                            mapOf("name" to "qalipsis.${protocolPathIdentifier}.bar.first")
                        )
                        it.prop(DataPoints::dataPoints).all {
                            hasSize(1)
                            containsAll(
                                DataPoints.DataPoint(1.1, now)
                            )
                        }
                    }
                    any {
                        it.prop(DataPoints::target)
                            .isEqualTo("qalipsis.${protocolPathIdentifier}.bar.second;tag1=value1;tag2=value2")
                        it.prop(DataPoints::tags)
                            .isEqualTo(
                                mapOf(
                                    "name" to "qalipsis.${protocolPathIdentifier}.bar.second",
                                    "tag1" to "value1",
                                    "tag2" to "value2"
                                )
                            )
                        it.prop(DataPoints::dataPoints).all {
                            hasSize(1)
                            containsAll(
                                DataPoints.DataPoint(-1.2, now.minusSeconds(1))
                            )
                        }
                    }
                    any {
                        it.prop(DataPoints::target)
                            .isEqualTo("qalipsis.${protocolPathIdentifier}.bar.third")
                        it.prop(DataPoints::tags).isEqualTo(
                            mapOf("name" to "qalipsis.${protocolPathIdentifier}.bar.third")
                        )
                        it.prop(DataPoints::dataPoints).all {
                            hasSize(2)
                            containsAll(
                                DataPoints.DataPoint(1.3, now.minusSeconds(2)),
                                DataPoints.DataPoint(1.4, now.minusSeconds(4))
                            )
                        }
                    }
                }

                confirmVerified(timeToResponse, recordsCount, eventsLogger, campaignMeterRegistry)
            }

        @Test
        @Timeout(10)
        fun `should fail when the step cannot start because the server does not exist`() =
            testDispatcherProvider.run {
                //given
                val startStopContext = relaxedMockk<StepStartStopContext> {
                    every { toEventTags() } returns emptyMap()
                    every { scenarioName } returns "scenario-test"
                    every { stepName } returns "step-test"
                }
                val now = Clock.tickSeconds(ZoneId.systemDefault()).instant()
                val capturedInput = slot<String>()
                val capturedContext = slot<StepContext<*, *>>()
                val saveStep = GraphiteSaveStep<String>(
                    id = "",
                    retryPolicy = null,
                    clientBuilder = {
                        GraphiteTcpClient("localhost", -1, listOfNotNull(recordEncoder))
                    },
                    eventsLogger = null,
                    meterRegistry = null,
                    messageFactory = { ctx, input ->
                        capturedContext.captured = ctx
                        capturedInput.captured = input

                        listOf(
                            GraphiteRecord("qalipsis.$protocolPathIdentifier.bar.first", now, 1.1),
                            GraphiteRecord(
                                "qalipsis.$protocolPathIdentifier.bar.second",
                                now.minusSeconds(1),
                                -1.2,
                                tags = mapOf("tag1" to "value1", "tag2" to "value2")
                            ),
                            GraphiteRecord("qalipsis.$protocolPathIdentifier.bar.third", now.minusSeconds(2), 1.3),
                            GraphiteRecord("qalipsis.$protocolPathIdentifier.bar.third", now.minusSeconds(4), 1.4)
                        )
                    }
                )
                // when
                assertThrows<IllegalArgumentException> {
                    saveStep.start(startStopContext)
                }

                // then
                confirmVerified(timeToResponse, recordsCount, eventsLogger, campaignMeterRegistry)
            }

        @Test
        @Timeout(10)
        fun `should fail when the messages cannot be sent because the client is closed`() =
            testDispatcherProvider.run {
                //given
                val startStopContext = relaxedMockk<StepStartStopContext> {
                    every { toEventTags() } returns emptyMap()
                    every { scenarioName } returns "scenario-test"
                    every { stepName } returns "step-test"
                }
                val now = Clock.tickSeconds(ZoneId.systemDefault()).instant()
                val capturedInput = slot<String>()
                val capturedContext = slot<StepContext<*, *>>()
                val client =
                    GraphiteTcpClient<GraphiteRecord>("localhost", actualGraphitePort, listOfNotNull(recordEncoder))
                val saveStep = GraphiteSaveStep<String>(
                    id = "",
                    retryPolicy = null,
                    clientBuilder = { client },
                    eventsLogger = null,
                    meterRegistry = null,
                    messageFactory = { ctx, input ->
                        capturedContext.captured = ctx
                        capturedInput.captured = input

                        listOf(
                            GraphiteRecord("qalipsis.$protocolPathIdentifier.bar.first", now, 1.1),
                            GraphiteRecord(
                                "qalipsis.$protocolPathIdentifier.bar.second",
                                now.minusSeconds(1),
                                -1.2,
                                tags = mapOf("tag1" to "value1", "tag2" to "value2")
                            ),
                            GraphiteRecord("qalipsis.$protocolPathIdentifier.bar.third", now.minusSeconds(2), 1.3),
                            GraphiteRecord("qalipsis.$protocolPathIdentifier.bar.third", now.minusSeconds(4), 1.4)
                        )
                    }
                )
                saveStep.start(startStopContext)
                val input = RandomStringUtils.randomAlphanumeric(10)
                val stepContext = StepTestHelper.createStepContext<String, GraphiteSaveResult<String>>(input = input)

                // when
                client.close()
                assertThrows<ClosedChannelException> {
                    saveStep.execute(stepContext)
                }
                saveStep.stop(startStopContext)

                // then
                assertThat(stepContext.output).isInstanceOf<ReceiveChannel<GraphiteSaveResult<String>>>()
                    .prop(ReceiveChannel<*>::isEmpty).isTrue()

                assertThat(capturedContext.captured).isSameAs(stepContext)
                assertThat(capturedInput.captured).isEqualTo(input)

                confirmVerified(timeToResponse, recordsCount, eventsLogger, campaignMeterRegistry)
            }
    }

    @Nested
    inner class PlaintextProtocol : ProtocolTest() {

        override val protocolPathIdentifier = "plaintext"

        override val recordEncoder = PlaintextEncoder()

        override val graphitePort = Constants.GRAPHITE_PLAINTEXT_PORT

    }

    @Nested
    inner class PickleProtocol : ProtocolTest() {

        override val protocolPathIdentifier = "pickle"

        override val recordEncoder = PickleEncoder()

        override val graphitePort = Constants.GRAPHITE_PICKLE_PORT

    }

    companion object {

        @Container
        @JvmStatic
        private val CONTAINER = GenericContainer<Nothing>(
            DockerImageName.parse(Constants.GRAPHITE_IMAGE_NAME)
        ).apply {
            withExposedPorts(Constants.HTTP_PORT, Constants.GRAPHITE_PLAINTEXT_PORT, Constants.GRAPHITE_PICKLE_PORT)
            setWaitStrategy(HttpWaitStrategy().forPort(Constants.HTTP_PORT).forPath("/render").forStatusCode(200))
            withStartupTimeout(Duration.ofSeconds(60))

            withCreateContainerCmdModifier { it.hostConfig!!.withMemory((512 * 1e20).toLong()).withCpuCount(2) }
            withClasspathResourceMapping("carbon.conf", Constants.CARBON_CONFIG_PATH, BindMode.READ_ONLY)
            withClasspathResourceMapping(
                "storage-schemas.conf",
                Constants.STORAGE_SCHEMA_CONFIG_PATH,
                BindMode.READ_ONLY
            )
        }
    }
}