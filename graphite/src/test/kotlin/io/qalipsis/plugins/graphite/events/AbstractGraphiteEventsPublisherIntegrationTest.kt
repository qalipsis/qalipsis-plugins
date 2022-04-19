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

package io.qalipsis.plugins.graphite.events

import io.micronaut.http.HttpStatus
import io.mockk.spyk
import io.qalipsis.api.events.Event
import io.qalipsis.api.events.EventLevel
import io.qalipsis.api.events.EventTag
import io.qalipsis.plugins.graphite.poll.model.events.model.GraphiteProtocol
import io.qalipsis.plugins.graphite.poll.model.events.GraphiteEventsConfiguration
import io.qalipsis.plugins.graphite.poll.model.events.GraphiteEventsPublisher
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.coVerifyNever
import kotlinx.coroutines.delay
import org.awaitility.kotlin.await
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.extension.RegisterExtension
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.HostPortWaitStrategy
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.TimeUnit

/**
 * @author rklymenko
 */
@Testcontainers
@Timeout(60)
internal abstract class AbstractGraphiteEventsPublisherIntegrationTest(private val protocol: GraphiteProtocol) {

    @JvmField
    @RegisterExtension
    val testDispatcherProvider = TestDispatcherProvider()

    private lateinit var configuration: GraphiteEventsConfiguration

    private val container = CONTAINER

    private var containerHttpPort = 2006

    private val httpClient = createSimpleHttpClient()

    private var protocolPort = 2006

    @BeforeAll
    fun setUp() {
        containerHttpPort = container.getMappedPort(HTTP_PORT)
        val request = generateHttpGet("http://$LOCALHOST_HOST:${containerHttpPort}/render")
        while (httpClient.send(request, HttpResponse.BodyHandlers.ofString()).statusCode() != HttpStatus.OK.code) {
            Thread.sleep(1_000)
        }

        protocolPort =
            if (protocol == GraphiteProtocol.PICKLE) container.getMappedPort(GRAPHITE_PICKLE_PORT)
            else container.getMappedPort(GRAPHITE_PLAINTEXT_PORT)
        val thatProtocol = protocol
        configuration = object : GraphiteEventsConfiguration {
            override val host: String
                get() = LOCALHOST_HOST
            override val port: Int
                get() = protocolPort
            override val protocol: GraphiteProtocol
                get() = thatProtocol
            override val batchSize: Int
                get() = 1
            override val lingerPeriod: Duration
                get() = Duration.ofSeconds(100)
            override val minLevel: EventLevel
                get() = EventLevel.INFO
            override val publishers: Int
                get() = 2
        }
    }

    @Test
    @Timeout(35)
    fun `should save single event into graphite`() = testDispatcherProvider.run {
        //given
        val graphiteEventsPublisher = GraphiteEventsPublisher(
            this,
            configuration
        )
        graphiteEventsPublisher.start()

        val key = "my.test.path.$protocol"
        val event = Event(key, EventLevel.INFO, emptyList(), 123)

        //when
        graphiteEventsPublisher.publish(event)

        //then
        val request =
            generateHttpGet("http://${configuration.host}:${containerHttpPort}/render?target=$key&format=json")
        await.atMost(30, TimeUnit.SECONDS).until {
            kotlin.runCatching {
                httpClient.send(request, HttpResponse.BodyHandlers.ofString()).body()
            }.getOrNull()?.contains(key) ?: false
        }
        graphiteEventsPublisher.stop()
    }

    @Test
    @Timeout(25)
    fun `should save multiple events one by one into graphite`() = testDispatcherProvider.run {
        //given
        val graphiteEventsPublisher = GraphiteEventsPublisher(
            this,
            configuration
        )
        graphiteEventsPublisher.start()
        val keys = (1..5).map { "my.tests-separate.$protocol.path$it" }
        val events = keys.map { Event(it, EventLevel.INFO, emptyList(), 123) }

        //when
        events.forEach { graphiteEventsPublisher.publish(it) }

        //then
        for (key in keys) {
            val request =
                generateHttpGet("http://${configuration.host}:${containerHttpPort}/render?target=$key&format=json")

            await.atMost(20, TimeUnit.SECONDS).until {
                kotlin.runCatching {
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString()).body()
                }.getOrNull()?.contains(key) ?: false
            }
        }
        graphiteEventsPublisher.stop()
    }

    @Test
    @Timeout(25)
    fun `should save multiple events all together into graphite`() = testDispatcherProvider.run {
        //given
        val graphiteEventsPublisher = GraphiteEventsPublisher(
            this,
            configuration
        )
        graphiteEventsPublisher.start()
        val keys = (1..5).map { "my.tests-altogether.$protocol.path$it" }
        val events = keys.map { Event(it, EventLevel.INFO, emptyList(), 123) }

        //when
        graphiteEventsPublisher.publish(events)

        //then
        for (key in keys) {
            val request =
                generateHttpGet("http://${configuration.host}:${containerHttpPort}/render?target=$key&format=json")

            await.atMost(20, TimeUnit.SECONDS).until {
                kotlin.runCatching {
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString()).body()
                }.getOrNull()?.contains(key) ?: false
            }
        }
        graphiteEventsPublisher.stop()
    }

    @Test
    @Timeout(25)
    fun `should save single event into graphite with tags`() = testDispatcherProvider.run {
        //given
        val graphiteEventsPublisher = GraphiteEventsPublisher(
            this,
            configuration
        )
        graphiteEventsPublisher.start()
        val key = "my.test.tagged.$protocol"
        val event = Event(key, EventLevel.INFO, listOf(EventTag("a", "1"), EventTag("b", "2")), 123.123)

        val url = StringBuilder()
        url.append("http://${configuration.host}:${containerHttpPort}/render?target=$key")
        for (tag in event.tags) {
            url.append(";")
            url.append(tag.key)
            url.append("=")
            url.append(tag.value)
        }
        url.append("&format=json")
        val request = generateHttpGet(url.toString())

        //when
        graphiteEventsPublisher.publish(event)

        //then
        await.atMost(20, TimeUnit.SECONDS).until {
            kotlin.runCatching {
                httpClient.send(request, HttpResponse.BodyHandlers.ofString()).body()
            }.getOrNull()?.contains(key) ?: false
        }
        graphiteEventsPublisher.stop()
    }

    @Test
    @Timeout(15)
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

    private fun generateHttpGet(uri: String): HttpRequest =
        HttpRequest.newBuilder()
            .GET()
            .uri(URI.create(uri))
            .build()

    private fun createSimpleHttpClient(): HttpClient =
        HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .build()!!


    companion object {

        private const val GRAPHITE_IMAGE_NAME = "graphiteapp/graphite-statsd:latest"
        const val HTTP_PORT = 80
        const val GRAPHITE_PLAINTEXT_PORT = 2003
        const val GRAPHITE_PICKLE_PORT = 2004
        const val LOCALHOST_HOST = "localhost"

        @Container
        @JvmStatic
        private val CONTAINER = GenericContainer<Nothing>(
            DockerImageName.parse(GRAPHITE_IMAGE_NAME)
        ).apply {
            setWaitStrategy(HostPortWaitStrategy())
            withExposedPorts(HTTP_PORT, GRAPHITE_PLAINTEXT_PORT, GRAPHITE_PICKLE_PORT)
            withAccessToHost(true)
            withStartupTimeout(Duration.ofSeconds(60))
            withCreateContainerCmdModifier { it.hostConfig!!.withMemory((512 * 1e20).toLong()).withCpuCount(2) }
        }
    }
}