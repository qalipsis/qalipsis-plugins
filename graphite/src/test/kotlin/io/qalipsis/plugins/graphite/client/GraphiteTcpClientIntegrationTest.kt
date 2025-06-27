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

package io.qalipsis.plugins.graphite.client

import assertk.all
import assertk.assertThat
import assertk.assertions.any
import assertk.assertions.containsAll
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.prop
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinFeature
import com.fasterxml.jackson.module.kotlin.jsonMapper
import com.fasterxml.jackson.module.kotlin.kotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import io.micronaut.core.io.socket.SocketUtils
import io.netty.channel.ChannelHandler.Sharable
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelOutboundHandlerAdapter
import io.netty.handler.codec.MessageToMessageEncoder
import io.qalipsis.plugins.graphite.Constants
import io.qalipsis.plugins.graphite.client.codecs.PickleEncoder
import io.qalipsis.plugins.graphite.client.codecs.PlaintextEncoder
import io.qalipsis.plugins.graphite.search.DataPoints
import io.qalipsis.test.coroutines.TestDispatcherProvider
import org.awaitility.kotlin.await
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.RegisterExtension
import org.testcontainers.containers.BindMode
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.HttpWaitStrategy
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import java.net.ConnectException
import java.net.URI
import java.net.UnknownHostException
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.TimeUnit


@Testcontainers
internal class GraphiteTcpClientIntegrationTest {

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

        private var httpPort: Int = -1

        private var actualGraphitePort: Int = -1

        @BeforeAll
        fun prepare() {
            httpPort = CONTAINER.getMappedPort(Constants.HTTP_PORT)
            actualGraphitePort = CONTAINER.getMappedPort(graphitePort)
        }

        @Test
        fun `should fail to connect to an invalid hostname`() = testDispatcherProvider.run {
            // given
            val client = buildClient<Any>(hostname = "unexisting-hostname")

            // when + then
            assertThrows<UnknownHostException> {
                client.open()
            }
        }

        @Test
        fun `should fail to connect to an invalid port`() = testDispatcherProvider.run {
            // given
            val client = buildClient<Any>(port = SocketUtils.findAvailableTcpPort())

            // when + then
            assertThrows<ConnectException> {
                client.open()
            }
        }

        @Test
        fun `should send a list of graphite records`() = testDispatcherProvider.run {
            // given
            val client = buildClient<GraphiteRecord>()
            client.open()

            // when
            val startTime = Clock.tickSeconds(ZoneId.of("UTC")).instant() - Duration.ofHours(3)

            client.send(
                listOf(
                    GraphiteRecord(
                        "qalipsis.${protocolPathIdentifier}.native",
                        startTime,
                        543,
                        "tag1" to "value1",
                        "tag2" to "value2"
                    ),
                    // Record with same path but different time and value.
                    GraphiteRecord(
                        "qalipsis.${protocolPathIdentifier}.native",
                        startTime + Duration.ofMinutes(30),
                        7254,
                        "tag1" to "value1",
                        "tag2" to "value2"
                    ),
                    // Record with same timestamp and path but different tags and value, with uppercase name and tags.
                    GraphiteRecord(
                        "QALIPSIS.${protocolPathIdentifier}.native",
                        startTime + Duration.ofMinutes(30),
                        815124L,
                        "TAG3" to "VALUE3",
                        "TAG4" to "VALUE4"
                    ),
                    // Record with same timestamp and path but different tags and value, including blanks in names and tags.
                    GraphiteRecord(
                        " QALIPSIS.${protocolPathIdentifier}.native.with blanks ",
                        startTime + Duration.ofMinutes(90),
                        54322.7265,
                        " tag 3 " to " value 3 ",
                        " tag 4 " to " value 4 "
                    ),
                    GraphiteRecord(
                        "QALIPSIS.${protocolPathIdentifier}.native.with-blanks",
                        startTime + Duration.ofHours(2),
                        -762,
                        " tag 3 " to " value 3 ",
                        " tag 4 " to " value 4 "
                    ),
                    GraphiteRecord(
                        "QALIPSIS.${protocolPathIdentifier}.native.value-without-tags",
                        startTime + Duration.ofMinutes(90),
                        -762
                    ),
                    GraphiteRecord(
                        "QALIPSIS.${protocolPathIdentifier}.native.value-without-tags",
                        startTime + Duration.ofMinutes(98),
                        232978.65
                    )
                )
            )

            // then
            verifyCreatedRecords(startTime, "native")
        }


        @Test
        fun `should send a list of entities convertible to records`() = testDispatcherProvider.run {
            // given
            val client = buildClient<DataPoints>(preEncoder = DataPointsEncoder())
            client.open()

            // when
            val startTime = Clock.tickSeconds(ZoneId.of("UTC")).instant() - Duration.ofHours(3)

            client.send(
                listOf(
                    DataPoints(
                        "",
                        mapOf(
                            "name" to "qalipsis.${protocolPathIdentifier}.converted",
                            "tag1" to "value1",
                            "tag2" to "value2"
                        ),
                        listOf(
                            DataPoints.DataPoint(543, startTime),
                            DataPoints.DataPoint(7254, startTime + Duration.ofMinutes(30))
                        )
                    ),
                    DataPoints(
                        "",
                        mapOf(
                            "name" to "qalipsis.${protocolPathIdentifier}.converted",
                            "tag3" to "value3",
                            "tag4" to "value4"
                        ),
                        listOf(
                            DataPoints.DataPoint(815124L, startTime + Duration.ofMinutes(30))
                        )
                    ),
                    DataPoints(
                        "",
                        mapOf(
                            "name" to "qalipsis.${protocolPathIdentifier}.converted.with-blanks",
                            "tag-3" to "value-3",
                            "tag-4" to "value-4"
                        ),
                        listOf(
                            DataPoints.DataPoint(54322.7265, startTime + Duration.ofMinutes(90)),
                            DataPoints.DataPoint(-762, startTime + Duration.ofHours(2))
                        )
                    ),
                    DataPoints(
                        "",
                        mapOf(
                            "name" to "qalipsis.${protocolPathIdentifier}.converted.value-without-tags"
                        ),
                        listOf(
                            DataPoints.DataPoint(-762, startTime + Duration.ofMinutes(90)),
                            DataPoints.DataPoint(232978.65, startTime + Duration.ofMinutes(98))
                        )
                    )
                )
            )

            // then
            verifyCreatedRecords(startTime, "converted")
        }

        private fun verifyCreatedRecords(startTime: Instant, secondaryIdentifier: String) {
            // Wait until the tags are properly created, since the operation is asynchronous.
            await.atMost(10, TimeUnit.SECONDS).until {
                val savedTagsResponse = httpClient.send(
                    HttpRequest.newBuilder()
                        .GET()
                        .uri(URI.create("http://localhost:$httpPort/tags/findSeries?pretty=1&expr=name=~qalipsis.${protocolPathIdentifier}.$secondaryIdentifier.*"))
                        .build(), HttpResponse.BodyHandlers.ofString()
                )
                savedTagsResponse.body() != "[]"
            }

            val savedValuesResponse = httpClient.send(
                HttpRequest.newBuilder()
                    .GET()
                    .uri(URI.create("http://localhost:$httpPort/render?target=qalipsis.${protocolPathIdentifier}.$secondaryIdentifier.**&target=seriesByTag('name=~qalipsis.${protocolPathIdentifier}.$secondaryIdentifier.*')&noNullPoints=True&format=json"))
                    .build(), HttpResponse.BodyHandlers.ofString()
            )
            val dataPoints = objectMapper.readValue<List<DataPoints>>(savedValuesResponse.body())
            assertThat(dataPoints).all {
                hasSize(4)
                any {
                    it.prop(DataPoints::target)
                        .isEqualTo("qalipsis.${protocolPathIdentifier}.$secondaryIdentifier.value-without-tags")
                    it.prop(DataPoints::tags)
                        .isEqualTo(mapOf("name" to "qalipsis.${protocolPathIdentifier}.$secondaryIdentifier.value-without-tags"))
                    it.prop(DataPoints::dataPoints).all {
                        hasSize(2)
                        containsAll(
                            DataPoints.DataPoint(-762.0, startTime + Duration.ofMinutes(90)),
                            DataPoints.DataPoint(232978.65, startTime + Duration.ofMinutes(98))
                        )
                    }
                }
                any {
                    it.prop(DataPoints::target)
                        .isEqualTo("qalipsis.${protocolPathIdentifier}.$secondaryIdentifier;tag1=value1;tag2=value2")
                    it.prop(DataPoints::tags)
                        .isEqualTo(
                            mapOf(
                                "name" to "qalipsis.${protocolPathIdentifier}.$secondaryIdentifier",
                                "tag1" to "value1",
                                "tag2" to "value2"
                            )
                        )
                    it.prop(DataPoints::dataPoints).all {
                        hasSize(2)
                        containsAll(
                            DataPoints.DataPoint(543.0, startTime),
                            DataPoints.DataPoint(7254.0, startTime + Duration.ofMinutes(30))
                        )
                    }
                }
                any {
                    it.prop(DataPoints::target)
                        .isEqualTo("qalipsis.${protocolPathIdentifier}.$secondaryIdentifier;tag3=value3;tag4=value4")
                    it.prop(DataPoints::tags)
                        .isEqualTo(
                            mapOf(
                                "name" to "qalipsis.${protocolPathIdentifier}.$secondaryIdentifier",
                                "tag3" to "value3",
                                "tag4" to "value4"
                            )
                        )
                    it.prop(DataPoints::dataPoints).all {
                        hasSize(1)
                        containsAll(
                            DataPoints.DataPoint(815124.0, startTime + Duration.ofMinutes(30))
                        )
                    }
                }
                any {
                    it.prop(DataPoints::target)
                        .isEqualTo("qalipsis.${protocolPathIdentifier}.$secondaryIdentifier.with-blanks;tag-3=value-3;tag-4=value-4")
                    it.prop(DataPoints::tags)
                        .isEqualTo(
                            mapOf(
                                "name" to "qalipsis.${protocolPathIdentifier}.$secondaryIdentifier.with-blanks",
                                "tag-3" to "value-3",
                                "tag-4" to "value-4"
                            )
                        )
                    it.prop(DataPoints::dataPoints).all {
                        hasSize(2)
                        containsAll(
                            DataPoints.DataPoint(54322.7265, startTime + Duration.ofMinutes(90)),
                            DataPoints.DataPoint(-762.0, startTime + Duration.ofHours(2))
                        )
                    }
                }
            }
        }

        protected fun <T : Any> buildClient(
            hostname: String = "localhost",
            port: Int = actualGraphitePort,
            preEncoder: ChannelOutboundHandlerAdapter? = null
        ): GraphiteTcpClient<T> {
            return GraphiteTcpClient(hostname, port, listOfNotNull(recordEncoder, preEncoder))
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

    /**
     * Encoder from [DataPoints] to [GraphiteRecord]s.
     */
    @Sharable
    class DataPointsEncoder : MessageToMessageEncoder<List<DataPoints>>() {
        override fun encode(ctx: ChannelHandlerContext, msg: List<DataPoints>, out: MutableList<Any>) {
            out.add(msg.flatMap { dataPoints ->
                dataPoints.dataPoints.map { dataPoint ->
                    GraphiteRecord(
                        dataPoints.tags["name"]!!,
                        dataPoint.timestamp,
                        dataPoint.value,
                        dataPoints.tags.minus("name")
                    )
                }
            })
        }

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