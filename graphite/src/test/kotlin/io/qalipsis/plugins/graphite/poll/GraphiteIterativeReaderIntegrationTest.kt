package io.qalipsis.plugins.graphite.poll

import assertk.all
import assertk.assertThat
import assertk.assertions.each
import assertk.assertions.hasSize
import assertk.assertions.isBetween
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import assertk.assertions.isNotNull
import assertk.assertions.prop
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.aerisconsulting.catadioptre.coInvokeInvisible
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.netty.channel.nio.NioEventLoopGroup
import io.qalipsis.api.events.Event
import io.qalipsis.api.events.EventLevel
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.plugins.graphite.poll.model.events.GraphiteEventsClient
import io.qalipsis.plugins.graphite.poll.model.events.model.GraphiteProtocol
import io.qalipsis.plugins.graphite.poll.model.GraphiteQuery
import io.qalipsis.plugins.graphite.render.model.GraphiteMetricsTime
import io.qalipsis.plugins.graphite.render.model.GraphiteMetricsTimeSignUnit
import io.qalipsis.plugins.graphite.render.model.GraphiteMetricsTimeUnit
import io.qalipsis.plugins.graphite.render.service.GraphiteRenderApiService
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.relaxedMockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.extension.RegisterExtension
import org.testcontainers.containers.BindMode
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.HostPortWaitStrategy
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import java.time.Duration

@WithMockk
@Testcontainers
internal class GraphiteIterativeReaderIntegrationTest {

    @JvmField
    @RegisterExtension
    val testDispatcherProvider = TestDispatcherProvider()

    private lateinit var renderApiService: GraphiteRenderApiService

    private lateinit var graphiteEventsClient: GraphiteEventsClient

    private lateinit var reader: GraphiteIterativeReader

    private val httpClient = HttpClient(CIO)

    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @BeforeAll
    @Timeout(25)
    fun setUpAll() = testDispatcherProvider.run {
        val serverUrl = "http://localhost:${CONTAINER.getMappedPort(HTTP_PORT)}"
        while (httpClient.get("${serverUrl}/render").status != HttpStatusCode.OK) {
            delay(500)
        }

        graphiteEventsClient = GraphiteEventsClient(
            protocolType = GraphiteProtocol.PLAINTEXT,
            host = "localhost",
            port = CONTAINER.getMappedPort(GRAPHITE_PLAINTEXT_PORT),
            coroutineScope = coroutineScope,
            workerGroup = NioEventLoopGroup()
        ).apply {
            open()
        }

        log.info { "HTTP path for Graphite: $serverUrl" }
        renderApiService = GraphiteRenderApiService(
            serverUrl = serverUrl,
            objectMapper = jacksonObjectMapper(),
            httpClient = httpClient,
            baseAuth = null
        )
    }

    @AfterAll
    internal fun tearDownAll() = testDispatcherProvider.run {
        graphiteEventsClient.close()
        renderApiService.close()
    }

    @Test
    @Timeout(1000)
    fun `should save data and poll them`() = testDispatcherProvider.run {
        // given
        val graphiteQuery = GraphiteQuery("exact.key.*").from(
            GraphiteMetricsTime(
                amount = 1,
                sign = GraphiteMetricsTimeSignUnit.MINUS,
                unit = GraphiteMetricsTimeUnit.MINUTES
            )
        ).noNullPoints(true)
        val pollStatement = GraphitePollStatement(graphiteQuery)
        reader = GraphiteIterativeReader(
            clientFactory = { renderApiService },
            pollStatement = pollStatement,
            pollDelay = Duration.ofMillis(300),
            coroutineScope = this,
            eventsLogger = null,
            meterRegistry = null
        )
        reader.init()

        // when
        reader.coInvokeInvisible<Unit>("poll", renderApiService)

        // then
        assertThat(reader.next()).prop(GraphiteQueryResult::results).isEmpty()



        // when
        graphiteEventsClient.publish((1..50).map { Event("exact.key.$it", EventLevel.INFO, value = it) })
        //FIXME Find a solution to remove the delay
        //Here is a part of graphite documentation, describing the timestamp:
        //https://graphite.readthedocs.io/en/latest/terminology.html
        delay(10000)
        reader.coInvokeInvisible<Unit>("poll", renderApiService) // Should only fetch the first record.

        // then
        assertThat(reader.next()).all {
            prop(GraphiteQueryResult::results).all {
                hasSize(50)
                each { it -> it.transform { it.dataPoints[0].value }.isNotNull().isBetween(1.0, 50.0) }
            }
            prop(GraphiteQueryResult::meters).all {
                prop(GraphiteQueryMeters::fetchedRecords).isEqualTo(50)
                prop(GraphiteQueryMeters::timeToResult).isGreaterThan(Duration.ZERO)
            }
        }

        // when
        graphiteEventsClient.publish((51..100).map { Event("exact.key.$it", EventLevel.INFO, value = it) })
        delay(2000)
        reader.coInvokeInvisible<Unit>("poll", renderApiService)

        // then
        assertThat(reader.next()).all {
            prop(GraphiteQueryResult::results).hasSize(50)
            prop(GraphiteQueryResult::meters).all {
                prop(GraphiteQueryMeters::fetchedRecords).isEqualTo(50)
                prop(GraphiteQueryMeters::timeToResult).isGreaterThan(Duration.ZERO)
            }
        }

        // when
        reader.coInvokeInvisible<Unit>("poll", renderApiService) // Should fetch no record.

        // then
        assertThat(reader.next()).all {
            prop(GraphiteQueryResult::results).all {
                hasSize(0)
            }
            prop(GraphiteQueryResult::meters).all {
                prop(GraphiteQueryMeters::fetchedRecords).isEqualTo(0)
                prop(GraphiteQueryMeters::timeToResult).isGreaterThan(Duration.ZERO)
            }
        }

        reader.stop(relaxedMockk())
    }

    private companion object {

        val log = logger()

        const val GRAPHITE_IMAGE_NAME = "graphiteapp/graphite-statsd:latest"

        const val HTTP_PORT = 80

        const val GRAPHITE_PLAINTEXT_PORT = 2003

        const val GRAPHITE_PICKLE_PORT = 2004

        const val CARBON_CONFIG_PATH = "/opt/graphite/conf/carbon.conf"

        @Container
        @JvmStatic
        val CONTAINER = GenericContainer<Nothing>(
            DockerImageName.parse(GRAPHITE_IMAGE_NAME)
        ).apply {
            setWaitStrategy(HostPortWaitStrategy())
            withExposedPorts(HTTP_PORT, GRAPHITE_PLAINTEXT_PORT, GRAPHITE_PICKLE_PORT)
            withAccessToHost(true)
            withStartupTimeout(Duration.ofSeconds(60))
            withCreateContainerCmdModifier { it.hostConfig!!.withMemory((512 * 1e20).toLong()).withCpuCount(2) }
            withClasspathResourceMapping("carbon.conf", CARBON_CONFIG_PATH, BindMode.READ_ONLY)
        }
    }
}
