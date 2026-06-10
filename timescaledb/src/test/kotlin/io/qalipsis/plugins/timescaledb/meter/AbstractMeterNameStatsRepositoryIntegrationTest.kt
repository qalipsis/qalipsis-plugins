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
import assertk.assertions.containsOnly
import assertk.assertions.hasSize
import assertk.assertions.index
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.key
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import io.micronaut.test.support.TestPropertyProvider
import io.qalipsis.plugins.timescaledb.meter.catadioptre.doPublish
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.r2dbc.postgresql.PostgresqlConnectionConfiguration
import io.r2dbc.postgresql.PostgresqlConnectionFactory
import io.r2dbc.spi.Connection
import jakarta.inject.Inject
import java.sql.Timestamp
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeUnit
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
internal abstract class AbstractMeterNameStatsRepositoryIntegrationTest : TestPropertyProvider {

    @Inject
    private lateinit var statsRepository: MeterNameStatsRepository

    @Inject
    protected lateinit var measurementPublisherFactory: TimescaledbMeasurementPublisherFactory

    @JvmField
    @RegisterExtension
    protected val testDispatcherProvider = TestDispatcherProvider()

    private lateinit var connection: PostgresqlConnectionFactory
    private lateinit var publisher: TimescaledbMeasurementPublisher

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
        publisher = measurementPublisherFactory.getPublisher() as TimescaledbMeasurementPublisher
    }

    @AfterEach
    fun tearDown() {
        Flux.usingWhen(
            connection.create(),
            { conn ->
                Mono.from(conn.createStatement("truncate table meters, meter_name_stats").execute())
            },
            Connection::close
        ).blockLast()
    }

    @Test
    @Timeout(20)
    internal fun `should return empty set when no cache entry exists for findFields`() = testDispatcherProvider.run {
        val result = statsRepository.findFields("unknown-tenant", "nonexistent-meter")

        assertThat(result).isEmpty()
    }

    @Test
    @Timeout(20)
    internal fun `should return cached fields after upsert`() = testDispatcherProvider.run {
        statsRepository.upsert("tenant-1", "my-meter", setOf("count", "max"), emptyMap())

        val result = statsRepository.findFields("tenant-1", "my-meter")

        assertThat(result).containsOnly("count", "max")
    }

    @Test
    @Timeout(20)
    internal fun `should not return fields belonging to a different tenant`() = testDispatcherProvider.run {
        statsRepository.upsert("tenant-1", "my-meter", setOf("value"), emptyMap())

        val result = statsRepository.findFields("tenant-2", "my-meter")

        assertThat(result).isEmpty()
    }

    @Test
    @Timeout(20)
    internal fun `should return empty map when no cache entry exists for findTags`() = testDispatcherProvider.run {
        val result = statsRepository.findTags("unknown-tenant", "nonexistent-meter")

        assertThat(result).isEmpty()
    }

    @Test
    @Timeout(20)
    internal fun `should return cached tags after upsert`() = testDispatcherProvider.run {
        statsRepository.upsert(
            "tenant-1", "my-meter", emptySet(),
            mapOf("env" to listOf("prod", "staging"), "zone" to listOf("eu"))
        )

        val result = statsRepository.findTags("tenant-1", "my-meter")

        assertThat(result).all {
            hasSize(2)
            key("env").containsOnly("prod", "staging")
            key("zone").containsOnly("eu")
        }
    }

    @Test
    @Timeout(20)
    internal fun `should not return tags belonging to a different tenant`() = testDispatcherProvider.run {
        statsRepository.upsert("tenant-1", "my-meter", emptySet(), mapOf("env" to listOf("prod")))

        val result = statsRepository.findTags("tenant-2", "my-meter")

        assertThat(result).isEmpty()
    }

    @Test
    @Timeout(20)
    internal fun `should overwrite fields and tags on conflict`() = testDispatcherProvider.run {
        statsRepository.upsert("tenant-1", "my-meter", setOf("count", "max"), mapOf("env" to listOf("prod")))
        statsRepository.upsert("tenant-1", "my-meter", setOf("sum", "mean"), mapOf("zone" to listOf("eu", "us")))

        val fields = statsRepository.findFields("tenant-1", "my-meter")
        assertThat(fields).containsOnly("sum", "mean")

        val tags = statsRepository.findTags("tenant-1", "my-meter")
        assertThat(tags).all {
            hasSize(1)
            key("zone").containsOnly("eu", "us")
        }
    }

    @Test
    @Timeout(20)
    internal fun `should return empty list when no meters exist`() = testDispatcherProvider.run {
        val result = statsRepository.findEntriesRequiringRefresh(10, Duration.ZERO)

        assertThat(result).isEmpty()
    }

    @Test
    @Timeout(20)
    internal fun `should return meters not yet present in stats`() = testDispatcherProvider.run {
        publisher.doPublish(
            listOf(
                TimescaledbMeter(
                    "meter-a",
                    tags = null,
                    timestamp = Timestamp.from(Instant.now()),
                    type = "gauge",
                    tenant = "tenant-1",
                    campaign = "any"
                ),
                TimescaledbMeter(
                    "meter-b",
                    tags = null,
                    timestamp = Timestamp.from(Instant.now()),
                    type = "gauge",
                    tenant = "tenant-1",
                    campaign = "any"
                ),
            )
        )

        val result = statsRepository.findEntriesRequiringRefresh(10, Duration.ZERO)

        assertThat(result).containsOnly("tenant-1" to "meter-a", "tenant-1" to "meter-b")
    }

    @Test
    @Timeout(20)
    internal fun `should deduplicate meters across multiple rows for the same name`() = testDispatcherProvider.run {
        // Same meter name inserted multiple times
        publisher.doPublish(
            (1..5).map {
                TimescaledbMeter(
                    "repeated-meter",
                    tags = null,
                    timestamp = Timestamp.from(Instant.now()),
                    type = "gauge",
                    tenant = "tenant-1",
                    campaign = "any"
                )
            }
        )

        val result = statsRepository.findEntriesRequiringRefresh(10, Duration.ZERO)

        assertThat(result).all {
            hasSize(1)
            index(0).isEqualTo("tenant-1" to "repeated-meter")
        }
    }

    @Test
    @Timeout(20)
    internal fun `should return oldest stats entries first when no new meters`() = testDispatcherProvider.run {
        Flux.usingWhen(
            connection.create(),
            { conn ->
                Flux.from(
                    conn.createStatement(
                        "INSERT INTO meter_name_stats (tenant, name, fields, tags, last_updated) VALUES " +
                                "('tenant-1', 'meter-old', '[]'::jsonb, '{}'::jsonb, NOW() - INTERVAL '10 minutes')," +
                                "('tenant-1', 'meter-recent', '[]'::jsonb, '{}'::jsonb, NOW() - INTERVAL '1 minute')"
                    ).execute()
                )
            },
            Connection::close
        ).blockLast()

        val result = statsRepository.findEntriesRequiringRefresh(10, Duration.ZERO)

        assertThat(result).all {
            hasSize(2)
            index(0).isEqualTo("tenant-1" to "meter-old")
            index(1).isEqualTo("tenant-1" to "meter-recent")
        }
    }

    @Test
    @Timeout(20)
    internal fun `should exclude stats entries younger than min-age`() = testDispatcherProvider.run {
        Flux.usingWhen(
            connection.create(),
            { conn ->
                Flux.from(
                    conn.createStatement(
                        "INSERT INTO meter_name_stats (tenant, name, fields, tags, last_updated) VALUES " +
                                "('tenant-1', 'meter-old', '[]'::jsonb, '{}'::jsonb, NOW() - INTERVAL '10 minutes')," +
                                "('tenant-1', 'meter-recent', '[]'::jsonb, '{}'::jsonb, NOW() - INTERVAL '1 minute')"
                    ).execute()
                )
            },
            Connection::close
        ).blockLast()

        val result = statsRepository.findEntriesRequiringRefresh(10, Duration.ofMinutes(5))

        assertThat(result).all {
            hasSize(1)
            index(0).isEqualTo("tenant-1" to "meter-old")
        }
    }

    @Test
    @Timeout(20)
    internal fun `should place new meters before existing stats entries`() = testDispatcherProvider.run {
        Flux.usingWhen(
            connection.create(),
            { conn ->
                Flux.from(
                    conn.createStatement(
                        "INSERT INTO meter_name_stats (tenant, name, fields, tags, last_updated) VALUES " +
                                "('tenant-1', 'meter-known', '[]'::jsonb, '{}'::jsonb, NOW() - INTERVAL '10 minutes')"
                    ).execute()
                )
            },
            Connection::close
        ).blockLast()
        publisher.doPublish(
            listOf(
                TimescaledbMeter(
                    "meter-new",
                    tags = null,
                    timestamp = Timestamp.from(Instant.now()),
                    type = "gauge",
                    tenant = "tenant-1",
                    campaign = "any"
                ),
            )
        )

        val result = statsRepository.findEntriesRequiringRefresh(10, Duration.ofMinutes(5))

        assertThat(result.first()).isEqualTo("tenant-1" to "meter-new")
    }

    @Test
    @Timeout(20)
    internal fun `should respect the limit parameter`() = testDispatcherProvider.run {
        publisher.doPublish(
            (1..10).map { i ->
                TimescaledbMeter(
                    "meter-$i",
                    tags = null,
                    timestamp = Timestamp.from(Instant.now()),
                    type = "gauge",
                    tenant = "tenant-1",
                    campaign = "any"
                )
            }
        )

        val result = statsRepository.findEntriesRequiringRefresh(3, Duration.ZERO)

        assertThat(result).hasSize(3)
    }

    companion object {
        const val DB_NAME = "qalipsis"
        const val USERNAME = "qalipsis_user"
        const val PASSWORD = "qalipsis-pwd"
        const val SCHEMA = "the_meters"
    }
}
