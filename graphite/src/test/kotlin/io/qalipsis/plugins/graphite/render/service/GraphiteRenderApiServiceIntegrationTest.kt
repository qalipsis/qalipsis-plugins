package io.qalipsis.plugins.graphite.render.service

import assertk.all
import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.index
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.key
import assertk.assertions.prop
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.netty.channel.nio.NioEventLoopGroup
import io.qalipsis.api.events.Event
import io.qalipsis.api.events.EventLevel
import io.qalipsis.plugins.graphite.Constants
import io.qalipsis.plugins.graphite.poll.model.events.GraphiteEventsClient
import io.qalipsis.plugins.graphite.poll.model.events.model.GraphiteProtocol
import io.qalipsis.plugins.graphite.render.model.GraphiteMetricsRequestBuilder
import io.qalipsis.plugins.graphite.render.model.GraphiteMetricsTime
import io.qalipsis.plugins.graphite.render.model.GraphiteMetricsTimeSignUnit
import io.qalipsis.plugins.graphite.render.model.GraphiteMetricsTimeUnit
import io.qalipsis.plugins.graphite.render.model.GraphiteRenderAggregationFuncName
import io.qalipsis.plugins.graphite.render.model.GraphiteRenderApiJsonResponse
import io.qalipsis.test.coroutines.TestDispatcherProvider
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

/**
 * @author rklymenko
 */
@Testcontainers
internal class GraphiteRenderApiServiceIntegrationTest {

    @JvmField
    @RegisterExtension
    val testDispatcherProvider = TestDispatcherProvider()

    private val httpClient = HttpClient(CIO)

    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val objectMapper = jacksonObjectMapper()

    private lateinit var graphiteEventsClient: GraphiteEventsClient

    private lateinit var renderApiService: GraphiteRenderApiService

    @BeforeAll
    @Timeout(25)
    fun setUpAll() = testDispatcherProvider.run {
        val serverUrl = "http://localhost:${CONTAINER.getMappedPort(Constants.HTTP_PORT)}"
        while (httpClient.get("${serverUrl}/render").status != HttpStatusCode.OK) {
            delay(1_000)
        }

        graphiteEventsClient = GraphiteEventsClient(
            protocolType = GraphiteProtocol.PLAINTEXT,
            host = "localhost",
            port = CONTAINER.getMappedPort(Constants.GRAPHITE_PLAINTEXT_PORT),
            coroutineScope = coroutineScope,
            workerGroup = NioEventLoopGroup()
        ).apply {
            open()
        }
        renderApiService = GraphiteRenderApiService(serverUrl, objectMapper, httpClient)
    }

    @AfterAll
    internal fun tearDownAll() = testDispatcherProvider.run {
        graphiteEventsClient.close()
        renderApiService.close()
        httpClient.close()
    }

    @Test
    @Timeout(3)
    fun `should read messages by key`() = testDispatcherProvider.run {
        //given
        val messageKey = "exact.key.1"

        //when
        graphiteEventsClient.publish(listOf(Event(messageKey, EventLevel.INFO, value = 123)))
        delay(1_000)

        //then
        val requestBuilder = GraphiteMetricsRequestBuilder(messageKey)
            .from(GraphiteMetricsTime(1, GraphiteMetricsTimeSignUnit.MINUS, GraphiteMetricsTimeUnit.MINUTES))
            .noNullPoints(true)
        val result = renderApiService.getAsJson(requestBuilder)
        assertThat(result).all {
            hasSize(1)
            index(0).all {
                prop(GraphiteRenderApiJsonResponse::target).isEqualTo(messageKey)
                prop(GraphiteRenderApiJsonResponse::tags).all {
                    hasSize(1)
                    key("name").isEqualTo(messageKey)
                }
            }
        }
    }

    @Test
    @Timeout(3)
    fun `should read messages by key and wildcard`() = testDispatcherProvider.run {
        //given
        val messageKey = "regex.key."
        val wildcard = "*"
        val range = (1..10)

        //when
        graphiteEventsClient.publish(range.map { Event(messageKey + it, EventLevel.INFO, value = 123) })
        delay(1_000)

        //then
        val requestBuilder = GraphiteMetricsRequestBuilder(messageKey + wildcard)
            .from(GraphiteMetricsTime(1, GraphiteMetricsTimeSignUnit.MINUS, GraphiteMetricsTimeUnit.MINUTES))
            .noNullPoints(true)
        val result = renderApiService.getAsJson(requestBuilder)
        assertThat(result).hasSize(10)
    }

    @Test
    @Timeout(3)
    fun `should read message by key and time interval`() = testDispatcherProvider.run {
        //given
        val messageKey = "exact.key.interval.1"

        //when
        graphiteEventsClient.publish(listOf(Event(messageKey, EventLevel.INFO, value = 123)))
        delay(1_000)

        //then
        val requestBuilder = GraphiteMetricsRequestBuilder(messageKey)
            .from(GraphiteMetricsTime(1, GraphiteMetricsTimeSignUnit.MINUS, GraphiteMetricsTimeUnit.MINUTES))
            .until(GraphiteMetricsTime(0, GraphiteMetricsTimeSignUnit.MINUS, GraphiteMetricsTimeUnit.MINUTES))
            .noNullPoints(true)
        val result = renderApiService.getAsJson(requestBuilder)
        assertThat(result).all {
            hasSize(1)
            index(0).all {
                prop(GraphiteRenderApiJsonResponse::target).isEqualTo(messageKey)
                prop(GraphiteRenderApiJsonResponse::tags).all {
                    hasSize(1)
                    key("name").isEqualTo(messageKey)
                }
            }
        }
    }

    @Test
    @Timeout(3)
    fun `should not read message by key outside of time interval with absolute time parameters`() =
        testDispatcherProvider.run {
            //given
            val messageKey = "exact.key.wrong-interval-abs.1"

            //when
            graphiteEventsClient.publish(listOf(Event(messageKey, EventLevel.INFO, value = 123)))
            delay(1_000)

            //then
            val requestBuilder = GraphiteMetricsRequestBuilder(messageKey)
                .from(1)
                .until(2)
                .noNullPoints(true)
            val result = renderApiService.getAsJson(requestBuilder)
            assertThat(result).isEmpty()
        }

    @Test
    @Timeout(3)
    fun `should not read message by key outside of time interval with relative time parameters`() =
        testDispatcherProvider.run {
            //given
            val messageKey = "exact.key.wrong-interval-rel.1"

            //when
            graphiteEventsClient.publish(listOf(Event(messageKey, EventLevel.INFO, value = 123)))
            delay(1_000)

            //then
            val requestBuilder = GraphiteMetricsRequestBuilder(messageKey)
                .from(GraphiteMetricsTime(3, GraphiteMetricsTimeSignUnit.MINUS, GraphiteMetricsTimeUnit.MINUTES))
                .until(GraphiteMetricsTime(2, GraphiteMetricsTimeSignUnit.MINUS, GraphiteMetricsTimeUnit.MINUTES))
                .noNullPoints(true)

            val result = renderApiService.getAsJson(requestBuilder)
            assertThat(result).isEmpty()
        }

    @Test
    @Timeout(3)
    fun `should read total by key`() = testDispatcherProvider.run {
        //given
        val messageKey = "exact.key.total.1"

        //when
        graphiteEventsClient.publish(listOf(Event(messageKey, EventLevel.INFO, value = 123)))
        delay(1_000)

        //then
        val requestBuilder = GraphiteMetricsRequestBuilder(messageKey)
            .aggregateFunction(GraphiteRenderAggregationFuncName.TOTAL)
            .from(GraphiteMetricsTime(1, GraphiteMetricsTimeSignUnit.MINUS, GraphiteMetricsTimeUnit.MINUTES))
            .noNullPoints(true)
        val result = renderApiService.getAsJson(requestBuilder)
        assertThat(result).all {
            hasSize(1)
            index(0).all {
                prop(GraphiteRenderApiJsonResponse::target).isEqualTo("totalSeries($messageKey)")
                prop(GraphiteRenderApiJsonResponse::tags).all {
                    key("name").isEqualTo(messageKey)
                    key("aggregatedBy").isEqualTo("total")
                }
            }
        }
    }

    @Test
    @Timeout(3)
    fun `should read sum by key`() = testDispatcherProvider.run {
        //given
        val messageKey = "exact.key.sum.1"

        //when
        graphiteEventsClient.publish(listOf(Event(messageKey, EventLevel.INFO, value = 123)))
        delay(1_000)

        //then
        val requestBuilder = GraphiteMetricsRequestBuilder(messageKey)
            .aggregateFunction(GraphiteRenderAggregationFuncName.SUM)
            .from(GraphiteMetricsTime(1, GraphiteMetricsTimeSignUnit.MINUS, GraphiteMetricsTimeUnit.MINUTES))
            .noNullPoints(true)
        val result = renderApiService.getAsJson(requestBuilder)
        assertThat(result).all {
            hasSize(1)
            index(0).all {
                prop(GraphiteRenderApiJsonResponse::target).isEqualTo("sumSeries($messageKey)")
                prop(GraphiteRenderApiJsonResponse::tags).all {
                    key("name").isEqualTo(messageKey)
                    key("aggregatedBy").isEqualTo("sum")
                }
            }
        }
    }

    @Test
    @Timeout(3)
    fun `should read max by key`() = testDispatcherProvider.run {
        //given
        val messageKey = "exact.key.max.1"

        //when
        graphiteEventsClient.publish(listOf(Event(messageKey, EventLevel.INFO, value = 123)))
        delay(1_000)

        //then
        val requestBuilder = GraphiteMetricsRequestBuilder(messageKey)
            .aggregateFunction(GraphiteRenderAggregationFuncName.MAX)
            .from(GraphiteMetricsTime(1, GraphiteMetricsTimeSignUnit.MINUS, GraphiteMetricsTimeUnit.MINUTES))
            .noNullPoints(true)

        val result = renderApiService.getAsJson(requestBuilder)
        assertThat(result).all {
            hasSize(1)
            index(0).all {
                prop(GraphiteRenderApiJsonResponse::target).isEqualTo("maxSeries($messageKey)")
                prop(GraphiteRenderApiJsonResponse::tags).all {
                    key("name").isEqualTo(messageKey)
                    key("aggregatedBy").isEqualTo("max")
                }
            }
        }
    }

    @Test
    @Timeout(3)
    fun `should read min by key`() = testDispatcherProvider.run {
        //given
        val messageKey = "exact.key.min.1"

        //when
        graphiteEventsClient.publish(listOf(Event(messageKey, EventLevel.INFO, value = 123)))
        delay(1_000)

        //then
        val requestBuilder = GraphiteMetricsRequestBuilder(messageKey)
            .aggregateFunction(GraphiteRenderAggregationFuncName.MIN)
            .from(GraphiteMetricsTime(1, GraphiteMetricsTimeSignUnit.MINUS, GraphiteMetricsTimeUnit.MINUTES))
            .noNullPoints(true)
        val result = renderApiService.getAsJson(requestBuilder)
        assertThat(result).all {
            hasSize(1)
            index(0).all {
                prop(GraphiteRenderApiJsonResponse::target).isEqualTo("minSeries($messageKey)")
                prop(GraphiteRenderApiJsonResponse::tags).all {
                    key("name").isEqualTo(messageKey)
                    key("aggregatedBy").isEqualTo("min")
                }
            }
        }
    }

    private companion object {

        @Container
        @JvmStatic
        val CONTAINER = GenericContainer<Nothing>(
            DockerImageName.parse(Constants.GRAPHITE_IMAGE_NAME)
        ).apply {
            setWaitStrategy(HostPortWaitStrategy())
            withExposedPorts(Constants.HTTP_PORT, Constants.GRAPHITE_PLAINTEXT_PORT, Constants.GRAPHITE_PICKLE_PORT)
            withAccessToHost(true)
            withStartupTimeout(Duration.ofSeconds(60))
            withCreateContainerCmdModifier { it.hostConfig!!.withMemory((512 * 1e20).toLong()).withCpuCount(2) }
            withClasspathResourceMapping("carbon.conf", Constants.CARBON_CONFIG_PATH, BindMode.READ_ONLY)
        }
    }
}
