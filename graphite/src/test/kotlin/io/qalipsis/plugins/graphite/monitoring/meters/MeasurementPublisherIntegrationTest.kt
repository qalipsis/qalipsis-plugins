/*
 * Copyright 2024 AERIS IT Solutions GmbH
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

package io.qalipsis.plugins.graphite.monitoring.meters

import assertk.all
import assertk.assertThat
import assertk.assertions.containsOnly
import assertk.assertions.hasSize
import assertk.assertions.index
import assertk.assertions.isEqualTo
import assertk.assertions.prop
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinFeature
import com.fasterxml.jackson.module.kotlin.jsonMapper
import com.fasterxml.jackson.module.kotlin.kotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import io.mockk.every
import io.mockk.mockk
import io.netty.channel.ChannelOutboundHandlerAdapter
import io.qalipsis.api.meters.Counter
import io.qalipsis.api.meters.DistributionMeasurementMetric
import io.qalipsis.api.meters.DistributionSummary
import io.qalipsis.api.meters.Gauge
import io.qalipsis.api.meters.MeasurementMetric
import io.qalipsis.api.meters.Meter
import io.qalipsis.api.meters.MeterSnapshot
import io.qalipsis.api.meters.MeterType
import io.qalipsis.api.meters.Statistic
import io.qalipsis.api.meters.Timer
import io.qalipsis.plugins.graphite.Constants
import io.qalipsis.plugins.graphite.GraphiteProtocol
import io.qalipsis.plugins.graphite.client.codecs.PickleEncoder
import io.qalipsis.plugins.graphite.client.codecs.PlaintextEncoder
import io.qalipsis.plugins.graphite.search.DataPoints
import io.qalipsis.test.coroutines.TestDispatcherProvider
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit
import org.awaitility.kotlin.await
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.extension.RegisterExtension
import org.testcontainers.containers.BindMode
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.HttpWaitStrategy
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName


@Testcontainers
internal class MeasurementPublisherIntegrationTest {

    private val httpClient = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build()

    private val objectMapper = jsonMapper().registerModules(kotlinModule {
        configure(KotlinFeature.NullToEmptyCollection, true)
        configure(KotlinFeature.NullToEmptyMap, true)
        configure(KotlinFeature.NullIsSameAsDefault, true)
    }, JavaTimeModule())

    abstract inner class ProtocolTest {

        @JvmField
        @RegisterExtension
        val testDispatcherProvider = TestDispatcherProvider()

        abstract val protocolPathIdentifier: String

        abstract val recordEncoder: ChannelOutboundHandlerAdapter

        abstract val graphitePort: Int

        abstract val protocol: GraphiteProtocol

        private var httpPort: Int = -1

        private lateinit var configuration: GraphiteMeasurementConfiguration

        private lateinit var graphiteMeasurementPublisher: GraphiteMeasurementPublisher

        @BeforeAll
        fun prepare() = testDispatcherProvider.run {
            httpPort = CONTAINER.getMappedPort(Constants.HTTP_PORT)
            configuration = object : GraphiteMeasurementConfiguration {
                override val host: String
                    get() = "localhost"
                override val port: Int
                    get() = CONTAINER.getMappedPort(graphitePort)
                override val protocol: GraphiteProtocol
                    get() = this@ProtocolTest.protocol
                override val batchSize: Int
                    get() = 1
                override val publishers: Int
                    get() = 1
                override val prefix: String
                    get() = "qalipsis.meters."
            }

            graphiteMeasurementPublisher = GraphiteMeasurementPublisher(
                configuration
            )
            graphiteMeasurementPublisher.init()
        }

        @AfterAll
        fun close() = testDispatcherProvider.run {
            graphiteMeasurementPublisher.stop()
        }

        @Test
        @Timeout(25)
        fun `should save multiple meter measurements with tags all together into graphite`() =
            testDispatcherProvider.run {
                //given

                val meterKeyPrefix = "$protocolPathIdentifier.collection"
                val keys = (1..4).map { "$meterKeyPrefix.item-$it" }

                val counterMock = mockk<Counter> {
                    every { id } returns mockk<Meter.Id> {
                        every { meterName } returns keys[0]
                        every { tags } returns emptyMap()
                        every { type } returns MeterType.COUNTER
                        every { scenarioName } returns "sc-1"
                        every { campaignKey } returns "campaign-1"
                        every { stepName } returns "step-1"
                    }
                }
                val gaugeMock = mockk<Gauge> {
                    every { id } returns mockk<Meter.Id> {
                        every { meterName } returns keys[1]
                        every { tags } returns mapOf("foo" to "bar", "cafe" to "one")
                        every { type } returns MeterType.GAUGE
                        every { scenarioName } returns "sc-1"
                        every { campaignKey } returns "campaign-1"
                        every { stepName } returns "step-1"
                    }
                }
                val timerMock = mockk<Timer> {
                    every { id } returns mockk<Meter.Id> {
                        every { meterName } returns keys[2]
                        every { tags } returns emptyMap()
                        every { type } returns MeterType.TIMER
                        every { scenarioName } returns "sc-1"
                        every { campaignKey } returns "campaign-1"
                        every { stepName } returns "step-1"
                    }
                }
                val summaryMock = mockk<DistributionSummary> {
                    every { id } returns mockk<Meter.Id> {
                        every { meterName } returns keys[3]
                        every { tags } returns mapOf("dist" to "summary", "local" to "host")
                        every { type } returns MeterType.DISTRIBUTION_SUMMARY
                        every { scenarioName } returns "sc-1"
                        every { campaignKey } returns "campaign-1"
                        every { stepName } returns "step-1"
                    }
                }
                val now = Instant.now()
                val countSnapshot = mockk<MeterSnapshot<Counter>> {
                    every { timestamp } returns now
                    every { meter } returns counterMock
                    every { measurements } returns listOf(MeasurementMetric(9.0, Statistic.COUNT))
                }
                val gaugeSnapshot = mockk<MeterSnapshot<Gauge>> {
                    every { timestamp } returns now
                    every { meter } returns gaugeMock
                    every { measurements } returns listOf(MeasurementMetric(5.0, Statistic.VALUE))
                }
                val timerSnapshot = mockk<MeterSnapshot<Timer>> {
                    every { timestamp } returns now
                    every { meter } returns timerMock
                    every { measurements } returns listOf(
                        MeasurementMetric(178713.0, Statistic.TOTAL_TIME),
                        MeasurementMetric(54328.5, Statistic.MAX)
                    )
                }
                val summarySnapshot = mockk<MeterSnapshot<DistributionSummary>> {
                    every { timestamp } returns now
                    every { meter } returns summaryMock
                    every { measurements } returns listOf(
                        MeasurementMetric(17873213.0, Statistic.TOTAL),
                        MeasurementMetric(548.5, Statistic.MEAN),
                        DistributionMeasurementMetric(548.5, Statistic.PERCENTILE, 74.5),
                    )
                }

                //when
                graphiteMeasurementPublisher.publish(
                    listOf(
                        countSnapshot,
                        gaugeSnapshot,
                        timerSnapshot,
                        summarySnapshot
                    )
                )

                //then
                // Wait until the tags are properly created, since the operation is asynchronous.
                val savedMetersKeyPrefix = "qalipsis.meters.${protocolPathIdentifier}.collection"
                await.atMost(10, TimeUnit.SECONDS).until {
                    val savedTagsResponse = httpClient.send(
                        HttpRequest.newBuilder()
                            .GET()
                            .uri(URI.create("http://localhost:$httpPort/tags/findSeries?pretty=1&expr=name=~$savedMetersKeyPrefix.*"))
                            .build(), HttpResponse.BodyHandlers.ofString()
                    )
                    savedTagsResponse.body() != "[]"
                }
                val savedMetersResponse = httpClient.send(
                    HttpRequest.newBuilder()
                        .GET()
                        .uri(URI.create("http://localhost:${httpPort}/render?target=$savedMetersKeyPrefix.**&target=seriesByTag('name=~$savedMetersKeyPrefix.*')&noNullPoints=True&format=json"))
                        .build(), HttpResponse.BodyHandlers.ofString()
                )
                val dataPoints = objectMapper.readValue<List<DataPoints>>(savedMetersResponse.body())
                assertThat(dataPoints).all {
                    hasSize(7)
                    index(0).all {
                        prop(DataPoints::target).isEqualTo("$savedMetersKeyPrefix.item-1;campaign=campaign-1;measurement=count;scenario=sc-1;step=step-1;type=counter")
                        prop(DataPoints::tags).isEqualTo(
                            mapOf(
                                "type" to "counter",
                                "campaign" to "campaign-1",
                                "scenario" to "sc-1",
                                "step" to "step-1",
                                "measurement" to "count",
                                "name" to "$savedMetersKeyPrefix.item-1"
                            )
                        )
                        prop(DataPoints::dataPoints)
                            .all {
                                hasSize(1)
                                containsOnly(
                                    DataPoints.DataPoint(
                                        9.0,
                                        now.truncatedTo(ChronoUnit.SECONDS)
                                    )
                                )
                            }
                    }
                    index(1).all {
                        prop(DataPoints::target).isEqualTo("$savedMetersKeyPrefix.item-2;cafe=one;campaign=campaign-1;foo=bar;measurement=value;scenario=sc-1;step=step-1;type=gauge")
                        prop(DataPoints::tags).isEqualTo(
                            mapOf(
                                "type" to "gauge",
                                "campaign" to "campaign-1",
                                "scenario" to "sc-1",
                                "step" to "step-1",
                                "measurement" to "value",
                                "name" to "$savedMetersKeyPrefix.item-2",
                                "foo" to "bar",
                                "cafe" to "one"
                            )
                        )
                        prop(DataPoints::dataPoints)
                            .all {
                                hasSize(1)
                                containsOnly(
                                    DataPoints.DataPoint(
                                        5.0,
                                        now.truncatedTo(ChronoUnit.SECONDS)
                                    )
                                )
                            }
                    }
                    index(2).all {
                        prop(DataPoints::target).isEqualTo("$savedMetersKeyPrefix.item-3;campaign=campaign-1;measurement=max;scenario=sc-1;step=step-1;type=timer")
                        prop(DataPoints::tags).isEqualTo(
                            mapOf(
                                "type" to "timer",
                                "campaign" to "campaign-1",
                                "scenario" to "sc-1",
                                "step" to "step-1",
                                "measurement" to "max",
                                "name" to "$savedMetersKeyPrefix.item-3",
                            )
                        )
                        prop(DataPoints::dataPoints)
                            .all {
                                hasSize(1)
                                containsOnly(
                                    DataPoints.DataPoint(
                                        54328.5,
                                        now.truncatedTo(ChronoUnit.SECONDS)
                                    )
                                )
                            }
                    }
                    index(3).all {
                        prop(DataPoints::target).isEqualTo("$savedMetersKeyPrefix.item-3;campaign=campaign-1;measurement=total_time;scenario=sc-1;step=step-1;type=timer")
                        prop(DataPoints::tags).isEqualTo(
                            mapOf(
                                "type" to "timer",
                                "campaign" to "campaign-1",
                                "scenario" to "sc-1",
                                "step" to "step-1",
                                "measurement" to "total_time",
                                "name" to "$savedMetersKeyPrefix.item-3",
                            )
                        )
                        prop(DataPoints::dataPoints)
                            .all {
                                hasSize(1)
                                containsOnly(
                                    DataPoints.DataPoint(
                                        178713.0,
                                        now.truncatedTo(ChronoUnit.SECONDS)
                                    )
                                )
                            }
                    }
                    index(4).all {
                        prop(DataPoints::target).isEqualTo("$savedMetersKeyPrefix.item-4;campaign=campaign-1;dist=summary;local=host;measurement=mean;scenario=sc-1;step=step-1;type=summary")
                        prop(DataPoints::tags).isEqualTo(
                            mapOf(
                                "type" to "summary",
                                "campaign" to "campaign-1",
                                "scenario" to "sc-1",
                                "step" to "step-1",
                                "measurement" to "mean",
                                "name" to "$savedMetersKeyPrefix.item-4",
                                "dist" to "summary",
                                "local" to "host"
                            )
                        )
                        prop(DataPoints::dataPoints)
                            .all {
                                hasSize(1)
                                containsOnly(
                                    DataPoints.DataPoint(
                                        548.5,
                                        now.truncatedTo(ChronoUnit.SECONDS)
                                    )
                                )
                            }
                    }
                    index(5).all {
                        prop(DataPoints::target).isEqualTo("$savedMetersKeyPrefix.item-4;campaign=campaign-1;dist=summary;local=host;measurement=percentile;percentile=74.5;scenario=sc-1;step=step-1;type=summary")
                        prop(DataPoints::tags).isEqualTo(
                            mapOf(
                                "type" to "summary",
                                "campaign" to "campaign-1",
                                "scenario" to "sc-1",
                                "step" to "step-1",
                                "measurement" to "percentile",
                                "percentile" to "74.5",
                                "name" to "$savedMetersKeyPrefix.item-4",
                                "dist" to "summary",
                                "local" to "host"
                            )
                        )
                        prop(DataPoints::dataPoints)
                            .all {
                                hasSize(1)
                                containsOnly(
                                    DataPoints.DataPoint(
                                        548.5,
                                        now.truncatedTo(ChronoUnit.SECONDS)
                                    )
                                )
                            }
                    }
                    index(6).all {
                        prop(DataPoints::target).isEqualTo("$savedMetersKeyPrefix.item-4;campaign=campaign-1;dist=summary;local=host;measurement=total;scenario=sc-1;step=step-1;type=summary")
                        prop(DataPoints::tags).isEqualTo(
                            mapOf(
                                "type" to "summary",
                                "campaign" to "campaign-1",
                                "scenario" to "sc-1",
                                "step" to "step-1",
                                "measurement" to "total",
                                "name" to "$savedMetersKeyPrefix.item-4",
                                "dist" to "summary",
                                "local" to "host"
                            )
                        )
                        prop(DataPoints::dataPoints)
                            .all {
                                hasSize(1)
                                containsOnly(
                                    DataPoints.DataPoint(
                                        1.7873213E7,
                                        now.truncatedTo(ChronoUnit.SECONDS)
                                    )
                                )
                            }
                    }
                }
            }

    }

    @Nested
    inner class PlaintextProtocol : ProtocolTest() {

        override val protocolPathIdentifier: String = "plaintext"

        override val recordEncoder: ChannelOutboundHandlerAdapter = PlaintextEncoder()

        override val graphitePort: Int = Constants.GRAPHITE_PLAINTEXT_PORT

        override val protocol: GraphiteProtocol = GraphiteProtocol.PLAINTEXT

    }

    @Nested
    inner class PickleProtocol : ProtocolTest() {

        override val protocolPathIdentifier: String = "pickle"

        override val recordEncoder: ChannelOutboundHandlerAdapter = PickleEncoder()

        override val graphitePort: Int = Constants.GRAPHITE_PICKLE_PORT

        override val protocol: GraphiteProtocol = GraphiteProtocol.PICKLE

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