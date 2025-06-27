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

package io.qalipsis.plugins.graphite.monitoring.events

import assertk.all
import assertk.assertThat
import assertk.assertions.any
import assertk.assertions.containsOnly
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.prop
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinFeature
import com.fasterxml.jackson.module.kotlin.jsonMapper
import com.fasterxml.jackson.module.kotlin.kotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import io.mockk.spyk
import io.netty.channel.ChannelOutboundHandlerAdapter
import io.qalipsis.api.events.Event
import io.qalipsis.api.events.EventLevel
import io.qalipsis.api.events.EventTag
import io.qalipsis.plugins.graphite.Constants
import io.qalipsis.plugins.graphite.GraphiteProtocol
import io.qalipsis.plugins.graphite.client.codecs.PickleEncoder
import io.qalipsis.plugins.graphite.client.codecs.PlaintextEncoder
import io.qalipsis.plugins.graphite.search.DataPoints
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.coVerifyNever
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
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
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit


@Testcontainers
internal class EventsPublisherIntegrationTest {

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

        private lateinit var configuration: GraphiteEventsConfiguration

        private lateinit var graphiteEventsPublisher: GraphiteEventsPublisher

        @BeforeAll
        fun prepare() {
            httpPort = CONTAINER.getMappedPort(Constants.HTTP_PORT)
            configuration = object : GraphiteEventsConfiguration {
                override val host: String
                    get() = "localhost"
                override val port: Int
                    get() = CONTAINER.getMappedPort(graphitePort)
                override val protocol: GraphiteProtocol
                    get() = this@ProtocolTest.protocol
                override val batchSize: Int
                    get() = 1
                override val lingerPeriod: Duration
                    get() = Duration.ofSeconds(100)
                override val minLevel: EventLevel
                    get() = EventLevel.INFO
                override val publishers: Int
                    get() = 2
                override val prefix: String
                    get() = "qalipsis.event."
            }

            graphiteEventsPublisher = GraphiteEventsPublisher(
                GlobalScope,
                configuration
            )
            graphiteEventsPublisher.start()
        }

        @AfterAll
        fun close() {
            graphiteEventsPublisher.stop()
        }

        @Test
        @Timeout(25)
        fun `should save single event into graphite`() = testDispatcherProvider.run {
            //given
            val key = "$protocolPathIdentifier.single.any"
            val event = Event(key, EventLevel.INFO, listOf(EventTag("tag-1", "value-1")), 123)

            //when
            graphiteEventsPublisher.publish(event)

            //then
            // Wait until the tags are properly created, since the operation is asynchronous.
            val keyPrefix = "qalipsis.event.${protocolPathIdentifier}.single"
            await.atMost(10, TimeUnit.SECONDS).until {
                val savedTagsResponse = httpClient.send(
                    HttpRequest.newBuilder()
                        .GET()
                        .uri(URI.create("http://localhost:$httpPort/tags/findSeries?pretty=1&expr=name=~$keyPrefix.*"))
                        .build(), HttpResponse.BodyHandlers.ofString()
                )
                savedTagsResponse.body() != "[]"
            }
            val savedEventsResponse = httpClient.send(
                HttpRequest.newBuilder()
                    .GET()
                    .uri(URI.create("http://localhost:${httpPort}/render?target=$keyPrefix.**&target=seriesByTag('name=~$keyPrefix.*')&noNullPoints=True&format=json"))
                    .build(), HttpResponse.BodyHandlers.ofString()
            )
            val dataPoints = objectMapper.readValue<List<DataPoints>>(savedEventsResponse.body())
            assertThat(dataPoints).all {
                hasSize(1)
                any {
                    it.prop(DataPoints::target).isEqualTo("$keyPrefix.any;level=info;tag-1=value-1")
                    it.prop(DataPoints::tags)
                        .isEqualTo(mapOf("name" to "$keyPrefix.any", "tag-1" to "value-1", "level" to "info"))
                    it.prop(DataPoints::dataPoints).all {
                        hasSize(1)
                        containsOnly(
                            DataPoints.DataPoint(123.0, event.timestamp.truncatedTo(ChronoUnit.SECONDS))
                        )
                    }
                }
            }
        }

        @Test
        @Timeout(25)
        fun `should save multiple events with tags all together into graphite`() = testDispatcherProvider.run {
            //given
            val eventKeyPrefix = "$protocolPathIdentifier.collection"
            val keys = (1..5).map { "$eventKeyPrefix.item-$it" }
            val events = keys.mapIndexed { index, k ->
                Event(
                    k, EventLevel.ERROR, listOf(
                        EventTag("tag-1", k.reversed()),
                        EventTag("tag-2", "$index")
                    ), 123 + index
                )
            }

            //when
            graphiteEventsPublisher.publish(events)

            //then
            // Wait until the tags are properly created, since the operation is asynchronous.
            val savedEventKeyPrefix = "qalipsis.event.${protocolPathIdentifier}.collection"
            await.atMost(10, TimeUnit.SECONDS).until {
                val savedTagsResponse = httpClient.send(
                    HttpRequest.newBuilder()
                        .GET()
                        .uri(URI.create("http://localhost:$httpPort/tags/findSeries?pretty=1&expr=name=~$savedEventKeyPrefix.*"))
                        .build(), HttpResponse.BodyHandlers.ofString()
                )
                savedTagsResponse.body() != "[]"
            }
            val savedEventsResponse = httpClient.send(
                HttpRequest.newBuilder()
                    .GET()
                    .uri(URI.create("http://localhost:${httpPort}/render?target=$savedEventKeyPrefix.**&target=seriesByTag('name=~$savedEventKeyPrefix.*')&noNullPoints=True&format=json"))
                    .build(), HttpResponse.BodyHandlers.ofString()
            )
            val dataPoints = objectMapper.readValue<List<DataPoints>>(savedEventsResponse.body())
            assertThat(dataPoints).all {
                hasSize(5)
                (1..5).forEach { index ->
                    any {
                        val referenceName = "$eventKeyPrefix.item-$index"
                        it.prop(DataPoints::target)
                            .isEqualTo("$savedEventKeyPrefix.item-$index;level=error;tag-1=${referenceName.reversed()};tag-2=${index - 1}")
                        it.prop(DataPoints::tags)
                            .isEqualTo(
                                mapOf(
                                    "name" to "$savedEventKeyPrefix.item-$index",
                                    "tag-1" to referenceName.reversed(),
                                    "tag-2" to "${index - 1}",
                                    "level" to "error"
                                )
                            )
                        it.prop(DataPoints::dataPoints).all {
                            hasSize(1)
                            //containsOnly(
                            //    DataPoints.DataPoint(
                            //        123.0 + index,
                            //        events[index].timestamp.truncatedTo(ChronoUnit.SECONDS)
                            //    )
                            //)
                        }
                    }
                }
            }
        }

        @Test
        @Timeout(25)
        fun `should save nothing due to insufficient event level`() = testDispatcherProvider.run {
            //given
            val graphiteEventsPublisher = spyk(
                GraphiteEventsPublisher(
                    this,
                    configuration
                )
            )
            graphiteEventsPublisher.start()
            val key = "fake-key.$protocol"
            val event = Event(key, EventLevel.TRACE, emptyList(), 123.123)

            //when
            graphiteEventsPublisher.publish(event)
            delay(200)

            //then
            coVerifyNever { graphiteEventsPublisher.publish(any<List<Event>>()) }
            graphiteEventsPublisher.stop()
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