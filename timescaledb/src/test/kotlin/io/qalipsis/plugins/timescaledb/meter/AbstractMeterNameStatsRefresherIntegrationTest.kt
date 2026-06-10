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
import assertk.assertions.contains
import assertk.assertions.containsAll
import assertk.assertions.containsNone
import assertk.assertions.containsOnly
import assertk.assertions.doesNotContain
import assertk.assertions.isEmpty
import assertk.assertions.key
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import io.micronaut.test.support.TestPropertyProvider
import io.qalipsis.plugins.timescaledb.meter.catadioptre.doPublish
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.r2dbc.postgresql.PostgresqlConnectionConfiguration
import io.r2dbc.postgresql.PostgresqlConnectionFactory
import io.r2dbc.spi.Connection
import jakarta.inject.Inject
import java.math.BigDecimal
import java.sql.Timestamp
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
internal abstract class AbstractMeterNameStatsRefresherIntegrationTest : TestPropertyProvider {

    @Inject
    private lateinit var refresher: MeterNameStatsRefresher

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
    internal fun `should handle empty meters table without error`() {
        refresher.refresh()

        // No exception thrown, nothing written
        testDispatcherProvider.run {
            val entries = statsRepository.findEntriesRequiringRefresh(10, java.time.Duration.ZERO)
            assertThat(entries).isEmpty()
        }
    }

    @Test
    @Timeout(20)
    internal fun `should detect count field as non-null`() = testDispatcherProvider.run {
        publisher.doPublish(
            listOf(
                TimescaledbMeter(
                    "my-meter", tags = null, timestamp = Timestamp.from(Instant.now()),
                    type = "counter", tenant = "t1", campaign = "c1",
                    count = BigDecimal("42")
                )
            )
        )

        refresher.refresh()

        val fields = statsRepository.findFields("t1", "my-meter")
        assertThat(fields).contains("count")
    }

    @Test
    @Timeout(20)
    internal fun `should detect value field as non-null`() = testDispatcherProvider.run {
        publisher.doPublish(
            listOf(
                TimescaledbMeter(
                    "my-meter", tags = null, timestamp = Timestamp.from(Instant.now()),
                    type = "gauge", tenant = "t1", campaign = "c1",
                    value = BigDecimal("3.14")
                )
            )
        )

        refresher.refresh()

        val fields = statsRepository.findFields("t1", "my-meter")
        assertThat(fields).contains("value")
    }

    @Test
    @Timeout(20)
    internal fun `should detect sum mean and max fields`() = testDispatcherProvider.run {
        publisher.doPublish(
            listOf(
                TimescaledbMeter(
                    "my-meter", tags = null, timestamp = Timestamp.from(Instant.now()),
                    type = "timer", tenant = "t1", campaign = "c1",
                    sum = BigDecimal("100"), mean = BigDecimal("50"), max = BigDecimal("90")
                )
            )
        )

        refresher.refresh()

        val fields = statsRepository.findFields("t1", "my-meter")
        assertThat(fields).containsOnly("sum", "mean", "max")
    }

    @Test
    @Timeout(20)
    internal fun `should detect active_tasks from other json`() = testDispatcherProvider.run {
        publisher.doPublish(
            listOf(
                TimescaledbMeter(
                    "my-meter", tags = null, timestamp = Timestamp.from(Instant.now()),
                    type = "executor", tenant = "t1", campaign = "c1",
                    other = """{"active_tasks": 5}"""
                )
            )
        )

        refresher.refresh()

        val fields = statsRepository.findFields("t1", "my-meter")
        assertThat(fields).contains("active_tasks")
    }

    @Test
    @Timeout(20)
    internal fun `should detect duration_nano from other json`() = testDispatcherProvider.run {
        publisher.doPublish(
            listOf(
                TimescaledbMeter(
                    "my-meter", tags = null, timestamp = Timestamp.from(Instant.now()),
                    type = "timer", tenant = "t1", campaign = "c1",
                    other = """{"duration_nano": 123456}"""
                )
            )
        )

        refresher.refresh()

        val fields = statsRepository.findFields("t1", "my-meter")
        assertThat(fields).contains("duration_nano")
    }

    @Test
    @Timeout(20)
    internal fun `should detect percentile fields from other json`() = testDispatcherProvider.run {
        publisher.doPublish(
            listOf(
                TimescaledbMeter(
                    "my-meter", tags = null, timestamp = Timestamp.from(Instant.now()),
                    type = "summary", tenant = "t1", campaign = "c1",
                    other = """{"percentile_99.9": 450.5, "percentile_50.0": 120.0}"""
                )
            )
        )

        refresher.refresh()

        val fields = statsRepository.findFields("t1", "my-meter")
        assertThat(fields).containsAll("percentile_99.9", "percentile_50.0")
    }

    @Test
    @Timeout(20)
    internal fun `should not report absent fields`() = testDispatcherProvider.run {
        publisher.doPublish(
            listOf(
                TimescaledbMeter(
                    "my-meter", tags = null, timestamp = Timestamp.from(Instant.now()),
                    type = "gauge", tenant = "t1", campaign = "c1",
                    value = BigDecimal("1")
                )
            )
        )

        refresher.refresh()

        val fields = statsRepository.findFields("t1", "my-meter")
        assertThat(fields).containsNone(
            "count",
            "sum",
            "mean",
            "max",
            "active_tasks",
            "duration_nano",
            "percentile_99.9"
        )
    }

    @Test
    @Timeout(20)
    internal fun `should collect distinct tag values`() = testDispatcherProvider.run {
        publisher.doPublish(
            listOf(
                TimescaledbMeter(
                    "my-meter", tags = """{"env":"prod","zone":"eu"}""",
                    timestamp = Timestamp.from(Instant.now()), type = "gauge", tenant = "t1", campaign = "c1"
                ),
                TimescaledbMeter(
                    "my-meter", tags = """{"env":"staging","zone":"eu"}""",
                    timestamp = Timestamp.from(Instant.now()), type = "gauge", tenant = "t1", campaign = "c1"
                ),
            )
        )

        refresher.refresh()

        val tags = statsRepository.findTags("t1", "my-meter")
        assertThat(tags).all {
            key("env").containsOnly("prod", "staging")
            key("zone").containsOnly("eu")
        }
    }

    @Test
    @Timeout(20)
    internal fun `should exclude dag tag from collected tags`() = testDispatcherProvider.run {
        publisher.doPublish(
            listOf(
                TimescaledbMeter(
                    "my-meter", tags = """{"dag":"some-dag","env":"prod"}""",
                    timestamp = Timestamp.from(Instant.now()), type = "gauge", tenant = "t1", campaign = "c1"
                )
            )
        )

        refresher.refresh()

        val tags = statsRepository.findTags("t1", "my-meter")
        assertThat(tags.keys).doesNotContain("dag")
        assertThat(tags).key("env").containsOnly("prod")
    }

    @Test
    @Timeout(20)
    internal fun `should exclude empty tag values`() = testDispatcherProvider.run {
        publisher.doPublish(
            listOf(
                TimescaledbMeter(
                    "my-meter", tags = """{"env":"","zone":"eu"}""",
                    timestamp = Timestamp.from(Instant.now()), type = "gauge", tenant = "t1", campaign = "c1"
                )
            )
        )

        refresher.refresh()

        val tags = statsRepository.findTags("t1", "my-meter")
        assertThat(tags.keys).doesNotContain("env")
        assertThat(tags).key("zone").containsOnly("eu")
    }

    @Test
    @Timeout(20)
    internal fun `should not mix fields and tags across tenants`() = testDispatcherProvider.run {
        publisher.doPublish(
            listOf(
                TimescaledbMeter(
                    "shared-name", tags = """{"env":"prod"}""",
                    timestamp = Timestamp.from(Instant.now()), type = "gauge", tenant = "t1", campaign = "c1",
                    value = BigDecimal("1")
                ),
                TimescaledbMeter(
                    "shared-name", tags = """{"region":"us"}""",
                    timestamp = Timestamp.from(Instant.now()), type = "counter", tenant = "t2", campaign = "c2",
                    count = BigDecimal("5")
                ),
            )
        )

        refresher.refresh()

        val fieldsT1 = statsRepository.findFields("t1", "shared-name")
        assertThat(fieldsT1).containsOnly("value")

        val fieldsT2 = statsRepository.findFields("t2", "shared-name")
        assertThat(fieldsT2).containsOnly("count")

        val tagsT1 = statsRepository.findTags("t1", "shared-name")
        assertThat(tagsT1.keys).doesNotContain("region")

        val tagsT2 = statsRepository.findTags("t2", "shared-name")
        assertThat(tagsT2.keys).doesNotContain("env")
    }

    @Test
    @Timeout(20)
    internal fun `should be idempotent on repeated refresh`() = testDispatcherProvider.run {
        publisher.doPublish(
            listOf(
                TimescaledbMeter(
                    "my-meter", tags = """{"env":"prod"}""",
                    timestamp = Timestamp.from(Instant.now()), type = "gauge", tenant = "t1", campaign = "c1",
                    value = BigDecimal("7")
                )
            )
        )

        refresher.refresh()
        refresher.refresh()

        val fields = statsRepository.findFields("t1", "my-meter")
        assertThat(fields).containsOnly("value")

        val tags = statsRepository.findTags("t1", "my-meter")
        assertThat(tags).key("env").containsOnly("prod")
    }

    @Test
    @Timeout(20)
    internal fun `should update stats when new meter data is added between refreshes`() = testDispatcherProvider.run {
        publisher.doPublish(
            listOf(
                TimescaledbMeter(
                    "my-meter", tags = null, timestamp = Timestamp.from(Instant.now()),
                    type = "gauge", tenant = "t1", campaign = "c1",
                    value = BigDecimal("1")
                )
            )
        )
        refresher.refresh()

        val fieldsBefore = statsRepository.findFields("t1", "my-meter")
        assertThat(fieldsBefore).containsOnly("value")

        publisher.doPublish(
            listOf(
                TimescaledbMeter(
                    "my-meter", tags = null, timestamp = Timestamp.from(Instant.now()),
                    type = "gauge", tenant = "t1", campaign = "c1",
                    value = BigDecimal("2"), max = BigDecimal("5")
                )
            )
        )
        refresher.refresh()

        val fieldsAfter = statsRepository.findFields("t1", "my-meter")
        assertThat(fieldsAfter).contains("max")
    }

    companion object {
        const val DB_NAME = "qalipsis"
        const val USERNAME = "qalipsis_user"
        const val PASSWORD = "qalipsis-pwd"
        const val SCHEMA = "the_meters"
    }
}
