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
import assertk.assertions.hasSize
import assertk.assertions.index
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.prop
import io.qalipsis.api.query.QueryAggregationOperator
import io.qalipsis.api.query.QueryClause
import io.qalipsis.api.query.QueryClauseOperator
import io.qalipsis.api.query.QueryDescription
import io.qalipsis.api.report.TimeSeriesAggregationResult
import io.qalipsis.plugins.timescaledb.TimescaleDbContainerProvider
import java.time.Duration
import kotlin.math.pow
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.DisabledIfSystemProperty
import org.testcontainers.containers.JdbcDatabaseContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.utility.DockerImageName

@DisabledIfSystemProperty(
    named = "os.arch",
    matches = "aarch.*",
    disabledReason = "The required functions only run in TimescaleDB HA, not supported on ARM"
)
internal class TimescaledbMeterQueryGeneratorIntegrationTest : AbstractMeterQueryGeneratorIntegrationTest() {

    override val dbPort: Int
        get() = db.firstMappedPort

    @Nested
    inner class AggregationCalculation : AbstractMeterQueryGeneratorIntegrationTest.AggregationCalculation() {

        @Test
        internal fun `should calculate the 75 percentile values`() = testDispatcherProvider.run {
            // given
            val query = meterQueryGenerator.prepareQueries(
                "tenant-1", QueryDescription(
                    filters = listOf(QueryClause("name", QueryClauseOperator.IS, "my-meter-1")),
                    fieldName = "value",
                    aggregationOperation = QueryAggregationOperator.PERCENTILE_75,
                    timeframeUnit = Duration.ofSeconds(2)
                )
            )
            val result = executeAggregation(query, start, latestTimestamp - timeStep, zones = setOf("zone-a"))

            // then 11 raw records since meters always return raw values regardless of aggregation operator
            assertThat(result).all {
                hasSize(11)
                index(0).all {
                    prop(TimeSeriesAggregationResult::start).isEqualTo(start)
                    prop(TimeSeriesAggregationResult::value).isNotNull().transform { it.toDouble() }.isEqualTo(2.0)
                }
                index(5).all {
                    prop(TimeSeriesAggregationResult::start).isEqualTo(start + Duration.ofMillis(2500))
                    prop(TimeSeriesAggregationResult::value).isNotNull().transform { it.toDouble() }.isEqualTo(21.0)
                }
                index(10).all {
                    prop(TimeSeriesAggregationResult::start).isEqualTo(start + Duration.ofSeconds(5))
                    prop(TimeSeriesAggregationResult::value).isNotNull().transform { it.toDouble() }.isEqualTo(233.0)
                }
            }
        }
    }

    companion object {

        @Container
        @JvmStatic
        val db: JdbcDatabaseContainer<*> = TimescaleDbContainerProvider()
            // The image timescaledb-ha is required for the hyperfunctions but is not compliant with ARM architectures.
            .newInstance(DockerImageName.parse("timescale/timescaledb-ha").withTag("pg14-latest"))
            .apply {
                withCreateContainerCmdModifier { cmd ->
                    cmd.hostConfig!!.withMemory(128 * 1024.0.pow(2).toLong()).withCpuCount(2)
                }
                waitingFor(Wait.forListeningPort())
                withStartupTimeout(Duration.ofSeconds(240))

                withDatabaseName(DB_NAME)
                withUsername(USERNAME)
                withPassword(PASSWORD)
                withCommand("postgres -c shared_preload_libraries=timescaledb -c log_error_verbosity=VERBOSE -c timescaledb.telemetry_level=OFF -c max_connections=100")
                withInitScript("pgsql-init-timescaledb.sql")
            }
    }
}