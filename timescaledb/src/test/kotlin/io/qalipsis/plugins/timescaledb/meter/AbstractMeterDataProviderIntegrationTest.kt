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

package io.qalipsis.plugins.timescaledb.meter

import assertk.all
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.containsOnly
import assertk.assertions.hasSize
import assertk.assertions.index
import assertk.assertions.isEqualTo
import assertk.assertions.key
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import io.micronaut.test.support.TestPropertyProvider
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.plugins.timescaledb.meter.catadioptre.doPublish
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.r2dbc.postgresql.PostgresqlConnectionConfiguration
import io.r2dbc.postgresql.PostgresqlConnectionFactory
import io.r2dbc.spi.Connection
import jakarta.inject.Inject
import java.sql.Timestamp
import java.time.Instant
import java.util.concurrent.TimeUnit
import org.apache.commons.lang3.RandomStringUtils
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.extension.RegisterExtension
import org.testcontainers.junit.jupiter.Testcontainers
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

@Testcontainers
@MicronautTest(environments = ["timescaledb", "head"], startApplication = false, transactional = false)
@Timeout(1, unit = TimeUnit.MINUTES)
internal abstract class AbstractMeterDataProviderIntegrationTest : TestPropertyProvider {

    @Inject
    private lateinit var meterDataProvider: TimescaledbMeterDataProvider

    @Inject
    private lateinit var statsRepository: MeterNameStatsRepository

    @JvmField
    @RegisterExtension
    protected val testDispatcherProvider = TestDispatcherProvider()

    private lateinit var connection: PostgresqlConnectionFactory

    @Inject
    protected lateinit var measurementPublisherFactory: TimescaledbMeasurementPublisherFactory

    private lateinit var timescaledbMeasurementPublisher: TimescaledbMeasurementPublisher

    abstract val dbPort: Int

    override fun getProperties(): Map<String, String> = mapOf(
        "meters.provider.timescaledb.enabled" to "true",
        "meters.provider.timescaledb.host" to "localhost",
        "meters.provider.timescaledb.port" to "$dbPort",
        "meters.provider.timescaledb.database" to DB_NAME,
        "meters.provider.timescaledb.username" to USERNAME,
        "meters.provider.timescaledb.password" to PASSWORD,
        "meters.provider.timescaledb.schema" to SCHEMA,
        "meters.provider.timescaledb.init-schema" to "true",

        "meters.export.enabled" to "true",
        "meters.export.timescaledb.enabled" to "true",
        "meters.export.timescaledb.host" to "localhost",
        "meters.export.timescaledb.port" to "$dbPort",
        "meters.export.timescaledb.database" to DB_NAME,
        "meters.export.timescaledb.username" to USERNAME,
        "meters.export.timescaledb.password" to PASSWORD,
        "meters.export.timescaledb.schema" to SCHEMA,
        "meters.export.timescaledb.init-schema" to "false",
    )

    @BeforeEach
    fun setUpAll() {
        connection = PostgresqlConnectionFactory(
            PostgresqlConnectionConfiguration.builder()
                .host("localhost").port(dbPort)
                .username(USERNAME)
                .password(PASSWORD)
                .database(DB_NAME)
                .schema(SCHEMA)
                .build()
        )
        timescaledbMeasurementPublisher = measurementPublisherFactory.getPublisher() as TimescaledbMeasurementPublisher
    }

    @AfterEach
    fun tearDown() {
        Flux.usingWhen(
            connection.create(),
            { connection ->
                Mono.from(
                    connection.createStatement("truncate table meters, meter_name_stats").execute()
                )
            },
            Connection::close
        ).blockLast()
    }

    @Test
    @Timeout(20)
    internal fun `should list the names without filter`() = testDispatcherProvider.run {
        // given
        // Index of constant size are used to make the verification of the alpha sorting easier.
        timescaledbMeasurementPublisher.doPublish((100..199).flatMap {
            listOf(
                // In the tenant 1, meters are saved twice to verify the distinct.
                TimescaledbMeter(
                    "my-meter-$it-gauge",
                    timestamp = Timestamp.from(Instant.now()),
                    type = "gauge",
                    tenant = "tenant-1",
                    campaign = "any",
                    tags = null
                ),
                TimescaledbMeter(
                    "my-meter-$it-gauge",
                    timestamp = Timestamp.from(Instant.now()),
                    type = "gauge",
                    tenant = "tenant-1",
                    campaign = "any",
                    tags = null
                ),
                TimescaledbMeter(
                    "my-meter-$it-timer",
                    timestamp = Timestamp.from(Instant.now()),
                    type = "timer",
                    tenant = "tenant-2",
                    campaign = "any",
                    tags = null
                )
            )
        })

        // when
        val allNamesOfTenant1 = meterDataProvider.searchNames("tenant-1", "any", emptySet(), 200)

        // then
        assertThat(allNamesOfTenant1.toList()).all {
            hasSize(100)
            (0..99).forEach { index ->
                index(index).isEqualTo("my-meter-${100 + index}-gauge")
            }
        }

        // when
        val someNamesOfTenant2 = meterDataProvider.searchNames("tenant-2", "any", emptySet(), 30)

        // then
        assertThat(someNamesOfTenant2.toList()).all {
            hasSize(30)
            (0..29).forEach { index ->
                index(index).isEqualTo("my-meter-${100 + index}-timer")
            }
        }
    }

    @Test
    @Timeout(20)
    internal fun `should list the names from the stats cache when no campaign is set`() = testDispatcherProvider.run {
        // given
        (100..199).forEach {
            statsRepository.upsert("tenant-1", "my-meter-$it-gauge", emptySet(), emptyMap())
            statsRepository.upsert("tenant-2", "my-meter-$it-timer", emptySet(), emptyMap())
        }
        // Only present in the meters table: not visible from the cache.
        timescaledbMeasurementPublisher.doPublish(
            listOf(
                TimescaledbMeter(
                    "uncached-meter",
                    timestamp = Timestamp.from(Instant.now()),
                    type = "gauge",
                    tenant = "tenant-1",
                    campaign = "any",
                    tags = null
                )
            )
        )

        // when
        val allNamesOfTenant1 = meterDataProvider.searchNames("tenant-1", null, emptySet(), 200)
        val blankCampaignNames = meterDataProvider.searchNames("tenant-1", " ", emptySet(), 200)
        val someNamesOfTenant2 = meterDataProvider.searchNames("tenant-2", null, emptySet(), 30)

        // then
        assertThat(allNamesOfTenant1.toList()).all {
            hasSize(100)
            (0..99).forEach { index ->
                index(index).isEqualTo("my-meter-${100 + index}-gauge")
            }
        }
        assertThat(blankCampaignNames).isEqualTo(allNamesOfTenant1)
        assertThat(someNamesOfTenant2.toList()).all {
            hasSize(30)
            (0..29).forEach { index ->
                index(index).isEqualTo("my-meter-${100 + index}-timer")
            }
        }
    }

    @Test
    @Timeout(20)
    internal fun `should list the names from the stats cache with filter when no campaign is set`() =
        testDispatcherProvider.run {
            // given
            (100..199).forEach {
                statsRepository.upsert("tenant-1", "my-meter-$it-gauge", emptySet(), emptyMap())
            }

            // when
            val result = meterDataProvider.searchNames("tenant-1", null, setOf("mY-mEteR-10*", "*-1?9-*"), 12)

            // then
            assertThat(result.toList()).containsExactly(
                "my-meter-100-gauge",
                "my-meter-101-gauge",
                "my-meter-102-gauge",
                "my-meter-103-gauge",
                "my-meter-104-gauge",
                "my-meter-105-gauge",
                "my-meter-106-gauge",
                "my-meter-107-gauge",
                "my-meter-108-gauge",
                "my-meter-109-gauge",
                "my-meter-119-gauge",
                "my-meter-129-gauge",
            )
        }

    @Test
    @Timeout(20)
    internal fun `should list the names from the meters table when no campaign is set and the cache is empty`() =
        testDispatcherProvider.run {
            // given
            timescaledbMeasurementPublisher.doPublish(
                listOf("meter-b", "meter-a", "meter-a").map {
                    TimescaledbMeter(
                        it,
                        timestamp = Timestamp.from(Instant.now()),
                        type = "gauge",
                        tenant = "tenant-1",
                        campaign = "any",
                        tags = null
                    )
                }
            )

            // when
            val result = meterDataProvider.searchNames("tenant-1", null, emptySet(), 10)

            // then
            assertThat(result.toList()).containsExactly("meter-a", "meter-b")
        }

    @Test
    @Timeout(20)
    internal fun `should list the names with filter`() = testDispatcherProvider.run {
        // given
        // Index of constant size are used to make the verification of the alpha sorting easier.
        timescaledbMeasurementPublisher.doPublish((100..199).flatMap {
            listOf(
                TimescaledbMeter(
                    "my-meter-$it-gauge",
                    timestamp = Timestamp.from(Instant.now()),
                    type = "gauge",
                    tenant = "tenant-1",
                    campaign = "any",
                    tags = null
                ),
                TimescaledbMeter(
                    "my-meter-$it-timer",
                    timestamp = Timestamp.from(Instant.now()),
                    type = "timer",
                    tenant = "tenant-2",
                    campaign = "any",
                    tags = null
                )
            )
        })
        val filters = setOf("mY-mEteR-10*", "*-1?9-*")

        // when
        var result = meterDataProvider.searchNames("tenant-1", "any", filters, 20)

        // then
        assertThat(result).all {
            hasSize(19)
            containsOnly(
                "my-meter-100-gauge",
                "my-meter-101-gauge",
                "my-meter-102-gauge",
                "my-meter-103-gauge",
                "my-meter-104-gauge",
                "my-meter-105-gauge",
                "my-meter-106-gauge",
                "my-meter-107-gauge",
                "my-meter-108-gauge",
                "my-meter-109-gauge",
                "my-meter-119-gauge",
                "my-meter-129-gauge",
                "my-meter-139-gauge",
                "my-meter-149-gauge",
                "my-meter-159-gauge",
                "my-meter-169-gauge",
                "my-meter-179-gauge",
                "my-meter-189-gauge",
                "my-meter-199-gauge"
            )
        }

        // when
        result = meterDataProvider.searchNames("tenant-2", "any", filters, 5)

        // then
        assertThat(result).all {
            hasSize(5)
            containsOnly(
                "my-meter-100-timer",
                "my-meter-101-timer",
                "my-meter-102-timer",
                "my-meter-103-timer",
                "my-meter-104-timer"
            )
        }
    }


    @Test
    @Timeout(20)
    internal fun `should list all the fields when the cache is empty`() = testDispatcherProvider.run {
        assertThat(meterDataProvider.listFields("tenant-1", "my-meter"))
            .isEqualTo(AbstractMeterQueryGenerator.FIELDS)
        assertThat(meterDataProvider.listFields("tenant-1", null)).isEqualTo(AbstractMeterQueryGenerator.FIELDS)
    }

    @Test
    @Timeout(20)
    internal fun `should list the cached fields of the meter`() = testDispatcherProvider.run {
        statsRepository.upsert("tenant-1", "meter-1", setOf("count", "max"), emptyMap())
        statsRepository.upsert("tenant-1", "meter-2", setOf("sum"), emptyMap())

        val result = meterDataProvider.listFields("tenant-1", "meter-1")

        assertThat(result.map { it.name }).containsOnly("count", "max")
    }

    @Test
    @Timeout(20)
    internal fun `should list the cached fields of all the meters when no name is set`() =
        testDispatcherProvider.run {
            statsRepository.upsert("tenant-1", "meter-1", setOf("count", "max"), emptyMap())
            statsRepository.upsert("tenant-1", "meter-2", setOf("max", "sum"), emptyMap())
            statsRepository.upsert("tenant-2", "meter-3", setOf("mean"), emptyMap())

            val result = meterDataProvider.listFields("tenant-1", null)

            assertThat(result.map { it.name }).containsOnly("count", "max", "sum")
        }

    @Test
    @Timeout(20)
    internal fun `should list the cached tags of the meter with filter`() = testDispatcherProvider.run {
        statsRepository.upsert(
            "tenant-1", "meter-1", emptySet(),
            mapOf("env" to listOf("prod", "staging"), "zone" to listOf("eu"))
        )
        statsRepository.upsert("tenant-1", "meter-2", emptySet(), mapOf("other" to listOf("x")))

        val all = meterDataProvider.searchTagsAndValues("tenant-1", "meter-1", emptySet(), 100)
        val filtered = meterDataProvider.searchTagsAndValues("tenant-1", "meter-1", setOf("en?"), 100)

        assertThat(all).all {
            hasSize(2)
            key("env").containsOnly("prod", "staging")
            key("zone").containsOnly("eu")
        }
        assertThat(filtered).all {
            hasSize(1)
            key("env").containsOnly("prod", "staging")
        }
    }

    @Test
    @Timeout(20)
    internal fun `should list the cached tags of all the meters when no name is set`() =
        testDispatcherProvider.run {
            statsRepository.upsert(
                "tenant-1", "meter-1", emptySet(),
                mapOf("env" to listOf("prod", "staging"), "zone" to listOf("eu"))
            )
            statsRepository.upsert("tenant-1", "meter-2", emptySet(), mapOf("env" to listOf("dev", "prod")))
            statsRepository.upsert("tenant-2", "meter-3", emptySet(), mapOf("other" to listOf("x")))

            val result = meterDataProvider.searchTagsAndValues("tenant-1", null, emptySet(), 100)

            assertThat(result).all {
                hasSize(2)
                key("env").containsOnly("dev", "prod", "staging")
                key("zone").containsOnly("eu")
            }
        }

    @Test
    @Timeout(20)
    internal fun `should list the tags without filters`() = testDispatcherProvider.run {
        // given
        timescaledbMeasurementPublisher.doPublish(
            listOf(
                TimescaledbMeter(
                    RandomStringUtils.randomAlphabetic(5),
                    timestamp = Timestamp.from(Instant.now()),
                    type = "gauge",
                    tenant = "tenant-1",
                    campaign = "any",
                    tags = """{"tag-1":"value-1"}"""
                ),
                TimescaledbMeter(
                    RandomStringUtils.randomAlphabetic(5),
                    timestamp = Timestamp.from(Instant.now()),
                    type = "gauge",
                    tenant = "tenant-1",
                    campaign = "any",
                    tags = """{"tag-1":"value-1","tag-2":"value-2","tag-3":""}"""
                ),
                TimescaledbMeter(
                    RandomStringUtils.randomAlphabetic(5),
                    timestamp = Timestamp.from(Instant.now()),
                    type = "gauge",
                    tenant = "tenant-1",
                    campaign = "any",
                    tags = """{"tag-1":"value-2","tag-2":"value-2","tag-3":"value-3"}"""
                ),
                TimescaledbMeter(
                    RandomStringUtils.randomAlphabetic(5),
                    timestamp = Timestamp.from(Instant.now()),
                    type = "type",
                    tenant = "tenant-2",
                    campaign = "any",
                    tags = """{"tag-2":"value-3"}"""
                )
            )
        )

        // when
        val allTagsOfTenant1 = meterDataProvider.searchTagsAndValues("tenant-1", null, emptySet(), 200)

        // then
        assertThat(allTagsOfTenant1).all {
            hasSize(3)
            key("tag-1").all {
                hasSize(2)
                containsOnly("value-1", "value-2")
            }
            key("tag-2").all {
                hasSize(1)
                containsOnly("value-2")
            }
            key("tag-3").all {
                hasSize(1)
                containsOnly("value-3")
            }
        }

        // when
        val allTagsOfTenant2 = meterDataProvider.searchTagsAndValues("tenant-2", null, emptySet(), 200)

        // then
        assertThat(allTagsOfTenant2).all {
            hasSize(1)
            key("tag-2").containsOnly("value-3")
        }

        // when
        val someTagsOfTenant1 = meterDataProvider.searchTagsAndValues("tenant-1", null, emptySet(), 2)

        // then
        assertThat(someTagsOfTenant1).all {
            hasSize(2)
            key("tag-1").containsOnly("value-1", "value-2")
            key("tag-2").containsOnly("value-2")
        }
    }

    @Test
    @Timeout(20)
    internal fun `should list the tags with filter`() = testDispatcherProvider.run {
        // given
        val name1 = RandomStringUtils.randomAlphabetic(5)
        val name2 = RandomStringUtils.randomAlphabetic(5)
        val name3 = RandomStringUtils.randomAlphabetic(5)
        val name4 = RandomStringUtils.randomAlphabetic(5)
        timescaledbMeasurementPublisher.doPublish(
            listOf(
                TimescaledbMeter(
                    name1,
                    timestamp = Timestamp.from(Instant.now()),
                    type = "gauge",
                    tenant = "tenant-1",
                    campaign = "any",
                    tags = """{"tag-1":"value-1"}"""
                ),
                TimescaledbMeter(
                    name2,
                    timestamp = Timestamp.from(Instant.now()),
                    type = "gauge",
                    tenant = "tenant-1",
                    campaign = "any",
                    tags = """{"tag-1":"value-1","tag-2":"value-2","tag-3":""}"""
                ),
                TimescaledbMeter(
                    name3,
                    timestamp = Timestamp.from(Instant.now()),
                    type = "gauge",
                    tenant = "tenant-1",
                    campaign = "any",
                    tags = """{"tag-1":"value-2","tag-2":"value-2","tag-3":"value-3"}"""
                ),
                TimescaledbMeter(
                    name4,
                    timestamp = Timestamp.from(Instant.now()),
                    type = "type",
                    tenant = "tenant-2",
                    campaign = "any",
                    tags = """{"tag-2":"value-3"}"""
                )
            )
        )

        // when
        var result = meterDataProvider.searchTagsAndValues("tenant-1", name2, setOf("tag-?"), 200)

        // then
        assertThat(result).all {
            hasSize(2)
            key("tag-1").all {
                hasSize(1)
                containsOnly("value-1")
            }
            key("tag-2").all {
                hasSize(1)
                containsOnly("value-2")
            }
        }

        // when
        result = meterDataProvider.searchTagsAndValues("tenant-1", name2, setOf("*-2"), 200)

        // then
        assertThat(result).all {
            hasSize(1)
            key("tag-2").containsOnly("value-2")
        }
    }

    companion object {

        val log = logger()

        /**
         * Default db name.
         */
        const val DB_NAME = "qalipsis"

        /**
         * Default username.
         */
        const val USERNAME = "qalipsis_user"

        /**
         * Default password.
         */
        const val PASSWORD = "qalipsis-pwd"

        /**
         * Default schema.
         */
        const val SCHEMA = "the_meters"

    }
}