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

package io.qalipsis.plugins.graphite.search

import assertk.all
import assertk.assertThat
import assertk.assertions.containsOnly
import assertk.assertions.hasSize
import assertk.assertions.index
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.key
import assertk.assertions.prop
import assertk.assertions.startsWith
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinFeature
import com.fasterxml.jackson.module.kotlin.jsonMapper
import com.fasterxml.jackson.module.kotlin.kotlinModule
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.qalipsis.plugins.graphite.Constants
import io.qalipsis.plugins.graphite.client.GraphiteRecord
import io.qalipsis.plugins.graphite.client.GraphiteTcpClient
import io.qalipsis.plugins.graphite.client.codecs.PickleEncoder
import io.qalipsis.test.coroutines.TestDispatcherProvider
import org.awaitility.kotlin.await
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
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
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Clock
import java.time.Duration
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * @author rklymenko
 */
@Testcontainers
internal class GraphiteRenderApiServiceIntegrationTest {

    @JvmField
    @RegisterExtension
    val testDispatcherProvider = TestDispatcherProvider()

    private var httpPort: Int = -1

    private val objectMapper = jsonMapper().registerModules(kotlinModule {
        configure(KotlinFeature.NullToEmptyCollection, true)
        configure(KotlinFeature.NullToEmptyMap, true)
        configure(KotlinFeature.NullIsSameAsDefault, true)
    }, JavaTimeModule())

    private val httpClient = HttpClient(CIO)

    private lateinit var renderApiService: GraphiteRenderApiService

    private val earliestRecordTimestamp = Clock.tickSeconds(ZoneId.of("UTC")).instant() - Duration.ofHours(3)

    @BeforeAll
    @Timeout(25)
    fun setUpAll() = testDispatcherProvider.run {
        httpPort = CONTAINER.getMappedPort(Constants.HTTP_PORT)

        val client = GraphiteTcpClient<GraphiteRecord>(
            "localhost",
            CONTAINER.getMappedPort(Constants.GRAPHITE_PICKLE_PORT),
            listOfNotNull(PickleEncoder())
        )
        client.open()
        val values = listOf(
            GraphiteRecord(
                "qalipsis.foo.native",
                earliestRecordTimestamp,
                543,
                "tag1" to "value1",
                "tag2" to "value2"
            ),
            GraphiteRecord(
                "qalipsis.foo.native",
                earliestRecordTimestamp + Duration.ofMinutes(30),
                7254,
                "tag1" to "value1",
                "tag2" to "value2"
            ),
            GraphiteRecord(
                "qalipsis.foo.native",
                earliestRecordTimestamp + Duration.ofMinutes(30),
                815124L,
                "TAG3" to "VALUE3",
                "TAG4" to "VALUE4"
            ),
            GraphiteRecord(
                " qalipsis.foo.with blanks ",
                earliestRecordTimestamp + Duration.ofMinutes(90),
                54322.7265,
                " tag 3 " to " value 3 ",
                " tag 4 " to " value 4 "
            ),
            GraphiteRecord(
                "qalipsis.foo.with-blanks",
                earliestRecordTimestamp + Duration.ofHours(2),
                -762,
                " tag 3 " to " value 3 ",
                " tag 4 " to " value 4 "
            ),
            GraphiteRecord(
                "qalipsis.foo.value-without-tags",
                earliestRecordTimestamp + Duration.ofMinutes(90),
                -762
            ),
            GraphiteRecord(
                "qalipsis.foo.value-without-tags",
                earliestRecordTimestamp + Duration.ofMinutes(98),
                232978.65
            )
        )

        client.send(values)
        client.close()

        // Wait until the tags are properly created, since the operation is asynchronous.
        val blockingHttpClient =
            java.net.http.HttpClient.newBuilder().version(java.net.http.HttpClient.Version.HTTP_1_1).build()
        await.atMost(10, TimeUnit.SECONDS).until {
            val savedTagsResponse = blockingHttpClient.send(
                HttpRequest.newBuilder()
                    .GET()
                    .uri(URI.create("http://localhost:$httpPort/tags/findSeries?pretty=1&expr=name=~qalipsis.foo.*"))
                    .build(), HttpResponse.BodyHandlers.ofString()
            )
            savedTagsResponse.body() != "[]"
        }

        renderApiService = GraphiteRenderApiService(
            "http://localhost:${CONTAINER.getMappedPort(Constants.HTTP_PORT)}/render",
            objectMapper,
            httpClient
        )
    }

    @AfterAll
    internal fun tearDownAll() = testDispatcherProvider.run {
        renderApiService.close()
        httpClient.close()
    }


    @Test
    @Timeout(3)
    fun `should read messages by exact key`() = testDispatcherProvider.run {
        // given
        var requestBuilder = GraphiteQuery("qalipsis.foo.value-without-tags")
            .from(earliestRecordTimestamp.minusSeconds(1))

        // when
        var result = renderApiService.execute(requestBuilder)

        // then
        assertThat(result).isInstanceOf<List<DataPoints>>().all {
            hasSize(1)
            index(0).all {
                prop(DataPoints::target).isEqualTo("qalipsis.foo.value-without-tags")
                prop(DataPoints::tags).all {
                    hasSize(1)
                    key("name").isEqualTo("qalipsis.foo.value-without-tags")
                }
                prop(DataPoints::dataPoints).all {
                    hasSize(2)
                    containsOnly(
                        DataPoints.DataPoint(-762.0, earliestRecordTimestamp + Duration.ofMinutes(90)),
                        DataPoints.DataPoint(232978.65, earliestRecordTimestamp + Duration.ofMinutes(98)),
                    )
                }
            }
        }

        // given the same request with a limited start
        requestBuilder = GraphiteQuery("qalipsis.foo.value-without-tags")
            .from(earliestRecordTimestamp + Duration.ofMinutes(93))

        // when
        result = renderApiService.execute(requestBuilder)

        // then
        assertThat(result).isInstanceOf<List<DataPoints>>().all {
            hasSize(1)
            index(0).all {
                prop(DataPoints::target).isEqualTo("qalipsis.foo.value-without-tags")
                prop(DataPoints::tags).all {
                    hasSize(1)
                    key("name").isEqualTo("qalipsis.foo.value-without-tags")
                }
                prop(DataPoints::dataPoints).all {
                    hasSize(1)
                    containsOnly(
                        DataPoints.DataPoint(232978.65, earliestRecordTimestamp + Duration.ofMinutes(98)),
                    )
                }
            }
        }

        // given the same request with a limited end
        requestBuilder = GraphiteQuery("qalipsis.foo.value-without-tags")
            .until(earliestRecordTimestamp + Duration.ofMinutes(93))

        // when
        result = renderApiService.execute(requestBuilder)

        // then
        assertThat(result).isInstanceOf<List<DataPoints>>().all {
            hasSize(1)
            index(0).all {
                prop(DataPoints::target).isEqualTo("qalipsis.foo.value-without-tags")
                prop(DataPoints::tags).all {
                    hasSize(1)
                    key("name").isEqualTo("qalipsis.foo.value-without-tags")
                }
                prop(DataPoints::dataPoints).all {
                    hasSize(1)
                    containsOnly(
                        DataPoints.DataPoint(-762.0, earliestRecordTimestamp + Duration.ofMinutes(90))
                    )
                }
            }
        }
    }

    @Test
    @Timeout(3)
    fun `should read messages by exact key and name of series`() = testDispatcherProvider.run {
        // given
        var requestBuilder = GraphiteQuery("qalipsis.foo.value-without-tags")
            .withTargetFromSeriesByTag("name", "qalipsis.foo.native")
            .from(earliestRecordTimestamp.minusSeconds(1))

        // when
        var result = renderApiService.execute(requestBuilder)

        // then
        assertThat(result).isInstanceOf<List<DataPoints>>().all {
            hasSize(3)
            index(0).all {
                prop(DataPoints::target).isEqualTo("qalipsis.foo.value-without-tags")
                prop(DataPoints::tags).all {
                    hasSize(1)
                    key("name").isEqualTo("qalipsis.foo.value-without-tags")
                }
                prop(DataPoints::dataPoints).all {
                    hasSize(2)
                    containsOnly(
                        DataPoints.DataPoint(-762.0, earliestRecordTimestamp + Duration.ofMinutes(90)),
                        DataPoints.DataPoint(232978.65, earliestRecordTimestamp + Duration.ofMinutes(98)),
                    )
                }
            }
            index(1).all {
                prop(DataPoints::target).isEqualTo("qalipsis.foo.native;tag1=value1;tag2=value2")
                prop(DataPoints::tags).all {
                    hasSize(3)
                    key("name").isEqualTo("qalipsis.foo.native")
                    key("tag1").isEqualTo("value1")
                    key("tag2").isEqualTo("value2")
                }
                prop(DataPoints::dataPoints).all {
                    hasSize(2)
                    containsOnly(
                        DataPoints.DataPoint(543.0, earliestRecordTimestamp),
                        DataPoints.DataPoint(7254.0, earliestRecordTimestamp + Duration.ofMinutes(30)),
                    )
                }
            }
            index(2).all {
                prop(DataPoints::target).isEqualTo("qalipsis.foo.native;tag3=value3;tag4=value4")
                prop(DataPoints::tags).all {
                    hasSize(3)
                    key("name").isEqualTo("qalipsis.foo.native")
                    key("tag3").isEqualTo("value3")
                    key("tag4").isEqualTo("value4")
                }
                prop(DataPoints::dataPoints).all {
                    hasSize(1)
                    containsOnly(
                        DataPoints.DataPoint(815124.0, earliestRecordTimestamp + Duration.ofMinutes(30)),
                    )
                }
            }
        }

        // given the same request with a limited start
        requestBuilder = GraphiteQuery("qalipsis.foo.value-without-tags")
            .withTargetFromSeriesByTag("name", "qalipsis.foo.native")
            .from(earliestRecordTimestamp + Duration.ofMinutes(20))

        // when
        result = renderApiService.execute(requestBuilder)

        // then
        assertThat(result).isInstanceOf<List<DataPoints>>().all {
            hasSize(3)
            index(0).all {
                prop(DataPoints::target).isEqualTo("qalipsis.foo.value-without-tags")
                prop(DataPoints::tags).all {
                    hasSize(1)
                    key("name").isEqualTo("qalipsis.foo.value-without-tags")
                }
                prop(DataPoints::dataPoints).all {
                    hasSize(2)
                    containsOnly(
                        DataPoints.DataPoint(-762.0, earliestRecordTimestamp + Duration.ofMinutes(90)),
                        DataPoints.DataPoint(232978.65, earliestRecordTimestamp + Duration.ofMinutes(98)),
                    )
                }
            }
            index(1).all {
                prop(DataPoints::target).isEqualTo("qalipsis.foo.native;tag1=value1;tag2=value2")
                prop(DataPoints::tags).all {
                    hasSize(3)
                    key("name").isEqualTo("qalipsis.foo.native")
                    key("tag1").isEqualTo("value1")
                    key("tag2").isEqualTo("value2")
                }
                prop(DataPoints::dataPoints).all {
                    hasSize(1)
                    containsOnly(
                        DataPoints.DataPoint(7254.0, earliestRecordTimestamp + Duration.ofMinutes(30)),
                    )
                }
            }
            index(2).all {
                prop(DataPoints::target).isEqualTo("qalipsis.foo.native;tag3=value3;tag4=value4")
                prop(DataPoints::tags).all {
                    hasSize(3)
                    key("name").isEqualTo("qalipsis.foo.native")
                    key("tag3").isEqualTo("value3")
                    key("tag4").isEqualTo("value4")
                }
                prop(DataPoints::dataPoints).all {
                    hasSize(1)
                    containsOnly(
                        DataPoints.DataPoint(815124.0, earliestRecordTimestamp + Duration.ofMinutes(30)),
                    )
                }
            }
        }

        // given the same request with a limited end
        requestBuilder = GraphiteQuery("qalipsis.foo.value-without-tags")
            .withTargetFromSeriesByTag("name", "qalipsis.foo.native")
            .until(earliestRecordTimestamp + Duration.ofMinutes(20))

        // when
        result = renderApiService.execute(requestBuilder)

        // then
        assertThat(result).isInstanceOf<List<DataPoints>>().all {
            hasSize(1)
            index(0).all {
                prop(DataPoints::target).isEqualTo("qalipsis.foo.native;tag1=value1;tag2=value2")
                prop(DataPoints::tags).all {
                    hasSize(3)
                    key("name").isEqualTo("qalipsis.foo.native")
                    key("tag1").isEqualTo("value1")
                    key("tag2").isEqualTo("value2")
                }
                prop(DataPoints::dataPoints).all {
                    hasSize(1)
                    containsOnly(
                        DataPoints.DataPoint(543.0, earliestRecordTimestamp),
                    )
                }
            }
        }
    }

    @Test
    @Timeout(3)
    fun `should read messages by patterned key`() = testDispatcherProvider.run {
        // given
        val requestBuilder = GraphiteQuery("qalipsis.foo.**")
            .from(earliestRecordTimestamp.minusSeconds(1))

        // when
        val result = renderApiService.execute(requestBuilder)

        // then
        assertThat(result).isInstanceOf<List<DataPoints>>().all {
            hasSize(1)
            index(0).all {
                prop(DataPoints::target).isEqualTo("qalipsis.foo.value-without-tags")
                prop(DataPoints::dataPoints).hasSize(2)
            }
        }
    }

    @Test
    @Timeout(3)
    fun `should read messages by patterned name of series`() = testDispatcherProvider.run {
        // given
        val requestBuilder = GraphiteQuery("unexisting-value")
            .withTargetFromSeriesByTag("name", "~qalipsis.foo.*")
            .from(earliestRecordTimestamp.minusSeconds(1))

        // when
        val result = renderApiService.execute(requestBuilder)

        // then
        assertThat(result).isInstanceOf<List<DataPoints>>().all {
            hasSize(3)
            index(0).all {
                prop(DataPoints::target).isEqualTo("qalipsis.foo.native;tag1=value1;tag2=value2")
                prop(DataPoints::dataPoints).hasSize(2)
            }
            index(1).all {
                prop(DataPoints::target).isEqualTo("qalipsis.foo.native;tag3=value3;tag4=value4")
                prop(DataPoints::dataPoints).hasSize(1)
            }
            index(2).all {
                prop(DataPoints::target).isEqualTo("qalipsis.foo.with-blanks;tag-3=value-3;tag-4=value-4")
                prop(DataPoints::dataPoints).hasSize(2)
            }
        }
    }

    @Test
    @Timeout(3)
    fun `should fail when sending to an invalid server`() = testDispatcherProvider.run {
        // given
        val renderApiService = GraphiteRenderApiService(
            "http://localhost:0/render",
            objectMapper,
            httpClient
        )
        val requestBuilder = GraphiteQuery("qalipsis.foo.**")
            .from(earliestRecordTimestamp.minusSeconds(1))

        // when
        val exception = assertThrows<GraphiteHttpQueryException> { renderApiService.execute(requestBuilder) }

        // then
        assertThat(exception.message).isNotNull().isEqualTo("Connection refused")
    }

    @Test
    @Timeout(3)
    fun `should fail when sending to an invalid URL`() = testDispatcherProvider.run {
        // given
        val renderApiService = GraphiteRenderApiService(
            "http://localhost:$httpPort/no-endpoint",
            objectMapper,
            httpClient
        )
        val requestBuilder = GraphiteQuery("qalipsis.foo.**")
            .from(earliestRecordTimestamp.minusSeconds(1))

        // when
        val exception = assertThrows<GraphiteHttpQueryException> { renderApiService.execute(requestBuilder) }

        // then
        assertThat(exception.message).isNotNull().startsWith("The HTTP query failed, HTTP status: 404")
    }

    private companion object {

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
