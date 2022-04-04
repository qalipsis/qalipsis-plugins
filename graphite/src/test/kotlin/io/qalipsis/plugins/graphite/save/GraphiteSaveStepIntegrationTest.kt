package io.qalipsis.plugins.graphite.save

import assertk.all
import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.index
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.startsWith
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tags
import io.micrometer.core.instrument.Timer
import io.micronaut.http.HttpStatus
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.verify
import io.netty.channel.nio.NioEventLoopGroup
import io.qalipsis.api.context.StepStartStopContext
import io.qalipsis.api.events.EventsLogger
import io.qalipsis.plugins.graphite.GraphiteClient
import io.qalipsis.test.assertk.prop
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.relaxedMockk
import kotlinx.coroutines.async
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.assertThrows
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

    private var containerHttpPort = -1

    private val httpClient = createSimpleHttpClient()

    private var protocolPort = -1

    private lateinit var client: GraphiteClient

    private val timeToResponse = relaxedMockk<Timer>()

    private val recordsCount = relaxedMockk<Counter>()

    private val eventsLogger = relaxedMockk<EventsLogger>()

    companion object {

        const val GRAPHITE_IMAGE_NAME = "graphiteapp/graphite-statsd:latest"
        const val HTTP_PORT = 80
        const val GRAPHITE_PLAINTEXT_PORT = 2003
        const val LOCALHOST_HOST = "localhost"

        @Container
        @JvmStatic
        private val CONTAINER = GenericContainer<Nothing>(
            DockerImageName.parse(GRAPHITE_IMAGE_NAME)
        ).apply {
            setWaitStrategy(HostPortWaitStrategy())
            withExposedPorts(HTTP_PORT, GRAPHITE_PLAINTEXT_PORT)
            withAccessToHost(true)
            withStartupTimeout(Duration.ofSeconds(60))
            withCreateContainerCmdModifier { it.hostConfig!!.withMemory((512 * 1e20).toLong()).withCpuCount(2) }
        }
    }

    @BeforeEach
    fun setUp() {
        containerHttpPort = container.getMappedPort(HTTP_PORT)
        val request = generateHttpGet("http://$LOCALHOST_HOST:${containerHttpPort}/render")
        while (httpClient.send(request, HttpResponse.BodyHandlers.ofString()).statusCode() != HttpStatus.OK.code) {
            Thread.sleep(1_000)
        }
        protocolPort = container.getMappedPort(GRAPHITE_PLAINTEXT_PORT)

        client = GraphiteClient(
            host = LOCALHOST_HOST,
            port = protocolPort,
            workerGroup = NioEventLoopGroup()
        )
    }

    @Test
    @Timeout(20)
    fun `should succeed when sending query with results`() = testDispatcherProvider.run {
        //given
        val metersTags = relaxedMockk<Tags>()
        val meterRegistry = relaxedMockk<MeterRegistry> {
            every { counter("graphite-save-saving-messages", refEq(metersTags)) } returns recordsCount
            every { timer("graphite-save-time-to-response", refEq(metersTags)) } returns timeToResponse
        }
        val startStopContext = relaxedMockk<StepStartStopContext> {
            every { toMetersTags() } returns metersTags
        }
        val results = mutableListOf<String>()
        val saveClient = GraphiteSaveMessageClientImpl(
            clientBuilder = { client },
            meterRegistry = meterRegistry,
            eventsLogger = eventsLogger
        )
        val tags: Map<String, String> = emptyMap()

        saveClient.start(startStopContext)

        val key = "foo.first"
        val keyTwo = "foo.second"
        val keyThird = "foo.third"

        val request = generateHttpGet("http://$LOCALHOST_HOST:${containerHttpPort}/render?target=$key&format=json")
        val requestTwo =
            generateHttpGet("http://$LOCALHOST_HOST:${containerHttpPort}/render?target=$keyTwo&format=json")
        val requestThird =
            generateHttpGet("http://$LOCALHOST_HOST:${containerHttpPort}/render?target=$keyThird&format=json")

        val now = Instant.now().toEpochMilli() / 1000

        //when+then
        async {
            val resultOfExecute = saveClient.execute(
                listOf(
                    "foo.first 1.1 $now\n", "foo.second 1.2 $now\n", "foo.third 1.3 $now\n"
                ), tags
            )
            assertThat(resultOfExecute).isInstanceOf(GraphiteSaveQueryMeters::class.java).all {
                prop("savedMessages").isEqualTo(3)
                prop("timeToResult").isNotNull()
            }
            verify {
                eventsLogger.debug("graphite.save.saving-messages", 3, any(), tags = tags)
                timeToResponse.record(more(0L), TimeUnit.NANOSECONDS)
                recordsCount.increment(3.0)
                eventsLogger.info("graphite.save.time-to-response", any<Duration>(), any(), tags = tags)
                eventsLogger.info("graphite.save.successes", any<Array<*>>(), any(), tags = tags)
            }
            confirmVerified(timeToResponse, recordsCount, eventsLogger)
        }

        //then
        await().atMost(10, TimeUnit.SECONDS)
            .until { httpClient.send(request, HttpResponse.BodyHandlers.ofString()).body().contains(key) }

        results.add(httpClient.send(request, HttpResponse.BodyHandlers.ofString()).body())
        results.add(httpClient.send(requestTwo, HttpResponse.BodyHandlers.ofString()).body())
        results.add(httpClient.send(requestThird, HttpResponse.BodyHandlers.ofString()).body())
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
        val metersTags = relaxedMockk<Tags>()
        val meterRegistry = relaxedMockk<MeterRegistry> {
            every { counter("graphite-save-saving-messages", refEq(metersTags)) } returns recordsCount
            every { timer("graphite-save-time-to-response", refEq(metersTags)) } returns timeToResponse
        }
        val startStopContext = relaxedMockk<StepStartStopContext> {
            every { toMetersTags() } returns metersTags
        }
        val results = mutableListOf<String>()
        val saveClient = GraphiteSaveMessageClientImpl(
            clientBuilder = { client },
            meterRegistry = meterRegistry,
            eventsLogger = eventsLogger
        )
        val tags: Map<String, String> = emptyMap()

        saveClient.start(startStopContext)

        val key = "hola.first"

        val request = generateHttpGet("http://$LOCALHOST_HOST:${containerHttpPort}/render?target=$key&format=json")

        //when+then
        async {
            assertThrows<Exception> {
                saveClient.execute(
                    listOf(
                        "hola.first 1.1", "hola.second 1.2"
                    ),
                    tags
                )
            }
        }
        //then
        results.add(httpClient.send(request, HttpResponse.BodyHandlers.ofString()).body())
        assertThat(results).all {
            hasSize(1)
            index(0).all {
                isEqualTo("[]")
            }
        }
    }

    private fun generateHttpGet(uri: String) =
        HttpRequest.newBuilder()
            .GET()
            .uri(URI.create(uri))
            .build()

    protected fun createSimpleHttpClient() =
        HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .build()
}