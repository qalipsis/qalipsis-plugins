package io.qalipsis.plugins.graphite.poll

import assertk.all
import assertk.assertThat
import assertk.assertions.containsAll
import assertk.assertions.hasSize
import assertk.assertions.index
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import assertk.assertions.prop
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.kotlinModule
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.qalipsis.plugins.graphite.Constants
import io.qalipsis.plugins.graphite.client.GraphiteClient
import io.qalipsis.plugins.graphite.client.GraphiteRecord
import io.qalipsis.plugins.graphite.client.GraphiteTcpClient
import io.qalipsis.plugins.graphite.client.codecs.PlaintextEncoder
import io.qalipsis.plugins.graphite.poll.catadioptre.poll
import io.qalipsis.plugins.graphite.search.DataPoints
import io.qalipsis.plugins.graphite.search.GraphiteQuery
import io.qalipsis.plugins.graphite.search.GraphiteRenderApiService
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.relaxedMockk
import org.awaitility.kotlin.await
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
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
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.TimeUnit

@WithMockk
@Testcontainers
internal class GraphiteIterativeReaderIntegrationTest {

    @JvmField
    @RegisterExtension
    val testDispatcherProvider = TestDispatcherProvider()

    val blockingHttpClient =
        java.net.http.HttpClient.newBuilder().version(java.net.http.HttpClient.Version.HTTP_1_1).build()

    private lateinit var renderApiService: GraphiteRenderApiService

    private lateinit var graphiteClient: GraphiteClient<GraphiteRecord>

    private lateinit var reader: GraphiteIterativeReader

    private var graphitePlaintextPort: Int = -1

    private var httpPort: Int = -1

    @BeforeAll
    fun setUpAll() = testDispatcherProvider.run {
        graphitePlaintextPort = CONTAINER.getMappedPort(Constants.GRAPHITE_PLAINTEXT_PORT)
        httpPort = CONTAINER.getMappedPort(Constants.HTTP_PORT)

        renderApiService = GraphiteRenderApiService(
            serverUrl = "http://localhost:$httpPort/render",
            objectMapper = jacksonObjectMapper().registerModules(kotlinModule {
                configure(KotlinFeature.NullToEmptyCollection, true)
                configure(KotlinFeature.NullToEmptyMap, true)
                configure(KotlinFeature.NullIsSameAsDefault, true)
            }, JavaTimeModule()),
            httpClient = HttpClient(CIO)
        )

        graphiteClient = GraphiteTcpClient(
            host = "localhost",
            port = graphitePlaintextPort,
            encoders = listOf(PlaintextEncoder())
        )
        graphiteClient.open()
    }

    @AfterAll
    internal fun tearDownAll() = testDispatcherProvider.run {
        graphiteClient.close()
        renderApiService.close()
    }

    @Test
    @Timeout(20)
    fun `should save data with and without tags and poll them`() = testDispatcherProvider.run {
        // given
        val graphiteQuery = GraphiteQuery("qalipsis.test.key.**")
            .withTargetFromSeriesByTag("name", "~qalipsis.test.key.*")
            .from(Instant.now().minusSeconds(600))

        val pollStatement = GraphitePollStatement(graphiteQuery)
        reader = GraphiteIterativeReader(
            clientFactory = { renderApiService },
            pollStatement = pollStatement,
            pollDelay = Duration.ofMillis(300),
            coroutineScope = this
        )
        reader.init()

        // when
        reader.poll()

        // then
        assertThat(reader.next()).prop(GraphiteQueryResult::results).isEmpty()

        // when
        val start = Clock.tickSeconds(ZoneId.systemDefault()).instant().minusSeconds(400)
        graphiteClient.send(((0L..50).map { index ->
            val keyIndex = index / 3 // We save every 3 values on the same key.
            GraphiteRecord("qalipsis.test.key.$keyIndex", start.plusSeconds(index), index)
        }))
        await.atMost(10, TimeUnit.SECONDS).until {
            blockingHttpClient.send(
                HttpRequest.newBuilder()
                    .GET()
                    .uri(URI.create("http://localhost:$httpPort/metrics/index.json"))
                    .build(), HttpResponse.BodyHandlers.ofString()
            ).body().run {
                contains("qalipsis.test.key.16") && contains("qalipsis.test.key.0")
            }
        }

        reader.poll()

        // then
        assertThat(reader.next()).all {
            prop(GraphiteQueryResult::results).all {
                hasSize(17)
                (0L..16).sortedBy { "$it" } // The keys have to be sorted by string, not by numbers.
                    .forEachIndexed { pointIndex, keyIndex ->
                        index(pointIndex).all {
                            prop(DataPoints::target).isEqualTo("qalipsis.test.key.$keyIndex")
                            prop(DataPoints::tags).isEqualTo(mapOf("name" to "qalipsis.test.key.$keyIndex"))
                            prop(DataPoints::dataPoints).all {
                                hasSize(3)
                                index(0).all {
                                    prop(DataPoints.DataPoint::value).isEqualTo(keyIndex.toDouble() * 3)
                                    prop(DataPoints.DataPoint::timestamp).isEqualTo(start.plusSeconds(keyIndex * 3))
                                }
                                index(1).all {
                                    prop(DataPoints.DataPoint::value).isEqualTo(keyIndex.toDouble() * 3 + 1)
                                    prop(DataPoints.DataPoint::timestamp)
                                        .isEqualTo(start.plusSeconds(keyIndex * 3 + 1))
                                }
                                index(2).all {
                                    prop(DataPoints.DataPoint::value).isEqualTo(keyIndex.toDouble() * 3 + 2)
                                    prop(DataPoints.DataPoint::timestamp)
                                        .isEqualTo(start.plusSeconds(keyIndex * 3 + 2))
                                }
                            }
                        }
                    }
            }
            prop(GraphiteQueryResult::meters).all {
                prop(GraphiteQueryMeters::fetchedRecords).isEqualTo(17)
                prop(GraphiteQueryMeters::timeToResult).isGreaterThan(Duration.ZERO)
            }
        }

        // when sending records with tags
        graphiteClient.send(((51L..98).map { index ->
            val keyIndex = index / 3 // We save every 3 values on the same key.
            GraphiteRecord(
                "qalipsis.test.key.$keyIndex",
                start.plusSeconds(index),
                index,
                mapOf("tag-1" to "$keyIndex", "tag-2" to "${2 * keyIndex}")
            )
        }))

        // Wait until the tags are properly created, since the operation is asynchronous.
        await.atMost(10, TimeUnit.SECONDS).until {
            val savedTagsResponse = blockingHttpClient.send(
                HttpRequest.newBuilder()
                    .GET()
                    .uri(URI.create("http://localhost:$httpPort/tags/findSeries?pretty=1&expr=name=~qalipsis.test.key.[17,34]"))
                    .build(), HttpResponse.BodyHandlers.ofString()
            )
            savedTagsResponse.body() != "[]"
        }
        reader.poll()

        // then
        assertThat(reader.next()).all {
            prop(GraphiteQueryResult::results).all {
                hasSize(16)
                (17L..32).forEachIndexed { pointIndex, keyIndex ->
                    index(pointIndex).all {
                        prop(DataPoints::target).isEqualTo("qalipsis.test.key.$keyIndex;tag-1=$keyIndex;tag-2=${2 * keyIndex}")
                        prop(DataPoints::tags).all {
                            hasSize(3)
                            containsAll(
                                "name" to "qalipsis.test.key.$keyIndex",
                                "tag-1" to "$keyIndex",
                                "tag-2" to "${2 * keyIndex}"
                            )
                        }
                        prop(DataPoints::dataPoints).all {
                            hasSize(3)
                            index(0).all {
                                prop(DataPoints.DataPoint::value).isEqualTo(keyIndex.toDouble() * 3)
                                prop(DataPoints.DataPoint::timestamp).isEqualTo(start.plusSeconds(keyIndex * 3))
                            }
                            index(1).all {
                                prop(DataPoints.DataPoint::value).isEqualTo(keyIndex.toDouble() * 3 + 1)
                                prop(DataPoints.DataPoint::timestamp).isEqualTo(start.plusSeconds(keyIndex * 3 + 1))
                            }
                            index(2).all {
                                prop(DataPoints.DataPoint::value).isEqualTo(keyIndex.toDouble() * 3 + 2)
                                prop(DataPoints.DataPoint::timestamp).isEqualTo(start.plusSeconds(keyIndex * 3 + 2))
                            }
                        }
                    }
                }
            }
            prop(GraphiteQueryResult::meters).all {
                prop(GraphiteQueryMeters::fetchedRecords).isEqualTo(16)
                prop(GraphiteQueryMeters::timeToResult).isGreaterThan(Duration.ZERO)
            }
        }

        reader.stop(relaxedMockk())
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
