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
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.prop
import io.mockk.every
import io.mockk.mockk
import io.qalipsis.api.meters.DistributionMeasurementMetric
import io.qalipsis.api.meters.MeasurementMetric
import io.qalipsis.api.meters.Meter
import io.qalipsis.api.meters.MeterSnapshot
import io.qalipsis.api.meters.MeterType
import io.qalipsis.api.meters.Statistic
import java.math.BigDecimal
import java.time.Instant
import org.junit.jupiter.api.Test

internal class TimescaledbMeterConverterTest {

    private val converter = TimescaledbMeterConverter()
    private val now = Instant.ofEpochMilli(1_000_000L)

    private fun buildSnapshot(
        name: String,
        type: MeterType,
        tags: Map<String, String>,
        measurements: List<io.qalipsis.api.meters.Measurement>,
    ): MeterSnapshot {
        val snapshot = mockk<MeterSnapshot>()
        every { snapshot.timestamp } returns now
        every { snapshot.meterId } returns Meter.Id(name, type, tags)
        every { snapshot.measurements } returns measurements
        return snapshot
    }

    @Test
    internal fun `given counter snapshot when convert then count is set`() {
        // given
        val snapshot = buildSnapshot(
            "my-counter", MeterType.COUNTER,
            mapOf("tenant" to "t1", "campaign" to "c1", "scenario" to "s1", "step" to "step-1"),
            listOf(MeasurementMetric(42.0, Statistic.COUNT))
        )

        // when
        val result = converter.convert(listOf(snapshot))

        // then
        assertThat(result).hasSize(1)
        assertThat(result[0]).all {
            prop(TimescaledbMeter::name).isEqualTo("my-counter")
            prop(TimescaledbMeter::type).isEqualTo("counter")
            prop(TimescaledbMeter::tenant).isEqualTo("t1")
            prop(TimescaledbMeter::campaign).isEqualTo("c1")
            prop(TimescaledbMeter::scenario).isEqualTo("s1")
            prop(TimescaledbMeter::tags).isEqualTo("""{"step":"step-1"}""")
            prop(TimescaledbMeter::count).isEqualTo(BigDecimal(42.0))
            prop(TimescaledbMeter::value).isNull()
            prop(TimescaledbMeter::sum).isNull()
            prop(TimescaledbMeter::mean).isNull()
            prop(TimescaledbMeter::max).isNull()
            prop(TimescaledbMeter::unit).isNull()
            prop(TimescaledbMeter::other).isNull()
        }
    }

    @Test
    internal fun `given gauge snapshot when convert then value is set`() {
        // given
        val snapshot = buildSnapshot(
            "my-gauge", MeterType.GAUGE,
            mapOf("campaign" to "c1", "scenario" to "s1"),
            listOf(MeasurementMetric(3.14, Statistic.VALUE))
        )

        // when
        val result = converter.convert(listOf(snapshot))

        // then
        assertThat(result).hasSize(1)
        assertThat(result[0]).all {
            prop(TimescaledbMeter::type).isEqualTo("gauge")
            prop(TimescaledbMeter::value).isEqualTo(BigDecimal(3.14))
            prop(TimescaledbMeter::count).isNull()
            prop(TimescaledbMeter::sum).isNull()
            prop(TimescaledbMeter::other).isNull()
        }
    }

    @Test
    internal fun `given timer snapshot when convert then count sum mean max and unit are set and percentiles in other`() {
        // given
        val snapshot = buildSnapshot(
            "my-timer", MeterType.TIMER,
            mapOf("campaign" to "c1", "scenario" to "s1"),
            listOf(
                MeasurementMetric(65.0, Statistic.COUNT),
                MeasurementMetric(224.0, Statistic.MEAN),
                MeasurementMetric(178713.0, Statistic.TOTAL_TIME),
                MeasurementMetric(54328.5, Statistic.MAX),
                DistributionMeasurementMetric(5432844.5, Statistic.PERCENTILE, 50.0),
                DistributionMeasurementMetric(500000448.5, Statistic.PERCENTILE, 85.0),
            )
        )

        // when
        val result = converter.convert(listOf(snapshot))

        // then
        assertThat(result).hasSize(1)
        assertThat(result[0]).all {
            prop(TimescaledbMeter::type).isEqualTo("timer")
            prop(TimescaledbMeter::count).isEqualTo(BigDecimal(65.0))
            prop(TimescaledbMeter::mean).isEqualTo(BigDecimal(224.0))
            prop(TimescaledbMeter::sum).isEqualTo(BigDecimal(178713.0))
            prop(TimescaledbMeter::max).isEqualTo(BigDecimal(54328.5))
            prop(TimescaledbMeter::unit).isEqualTo("MICROSECONDS")
            prop(TimescaledbMeter::value).isNull()
            prop(TimescaledbMeter::other).isNotNull()
                .isEqualTo("""{"percentile_50.0":"5432844.5","percentile_85.0":"500000448.5"}""")
        }
    }

    @Test
    internal fun `given distribution summary snapshot when convert then count sum mean max are set and percentiles in other`() {
        // given
        val snapshot = buildSnapshot(
            "my-summary", MeterType.DISTRIBUTION_SUMMARY,
            mapOf("campaign" to "c1", "scenario" to "s1"),
            listOf(
                MeasurementMetric(70.0, Statistic.COUNT),
                MeasurementMetric(17873213.0, Statistic.TOTAL),
                MeasurementMetric(12.5, Statistic.MEAN),
                MeasurementMetric(548.5, Statistic.MAX),
                DistributionMeasurementMetric(54328.5, Statistic.PERCENTILE, 50.0),
                DistributionMeasurementMetric(548.5, Statistic.PERCENTILE, 85.0),
            )
        )

        // when
        val result = converter.convert(listOf(snapshot))

        // then
        assertThat(result).hasSize(1)
        assertThat(result[0]).all {
            prop(TimescaledbMeter::type).isEqualTo("summary")
            prop(TimescaledbMeter::count).isEqualTo(BigDecimal(70.0))
            prop(TimescaledbMeter::sum).isEqualTo(BigDecimal(17873213.0))
            prop(TimescaledbMeter::mean).isEqualTo(BigDecimal(12.5))
            prop(TimescaledbMeter::max).isEqualTo(BigDecimal(548.5))
            prop(TimescaledbMeter::unit).isNull()
            prop(TimescaledbMeter::value).isNull()
            prop(TimescaledbMeter::other).isNotNull()
                .isEqualTo("""{"percentile_50.0":"54328.5","percentile_85.0":"548.5"}""")
        }
    }

    @Test
    internal fun `given statistics snapshot when convert then count sum mean max are set and percentiles in other`() {
        // given
        val snapshot = buildSnapshot(
            "my-statistics", MeterType.STATISTICS,
            mapOf("campaign" to "c1", "scenario" to "s1", "step" to "step-1"),
            listOf(
                MeasurementMetric(100.0, Statistic.COUNT),
                MeasurementMetric(5000.0, Statistic.TOTAL),
                MeasurementMetric(50.0, Statistic.MEAN),
                MeasurementMetric(200.0, Statistic.MAX),
                DistributionMeasurementMetric(180.0, Statistic.PERCENTILE, 95.0),
                DistributionMeasurementMetric(120.0, Statistic.PERCENTILE, 50.0),
            )
        )

        // when
        val result = converter.convert(listOf(snapshot))

        // then
        assertThat(result).hasSize(1)
        assertThat(result[0]).all {
            prop(TimescaledbMeter::name).isEqualTo("my-statistics")
            prop(TimescaledbMeter::type).isEqualTo("statistics")
            prop(TimescaledbMeter::count).isEqualTo(BigDecimal(100.0))
            prop(TimescaledbMeter::sum).isEqualTo(BigDecimal(5000.0))
            prop(TimescaledbMeter::mean).isEqualTo(BigDecimal(50.0))
            prop(TimescaledbMeter::max).isEqualTo(BigDecimal(200.0))
            prop(TimescaledbMeter::unit).isNull()
            prop(TimescaledbMeter::value).isNull()
            prop(TimescaledbMeter::other).isNotNull()
                .isEqualTo("""{"percentile_50.0":"120","percentile_95.0":"180"}""")
        }
    }

    @Test
    internal fun `given statistics snapshot without percentiles when convert then other is null`() {
        // given
        val snapshot = buildSnapshot(
            "my-statistics", MeterType.STATISTICS,
            mapOf("campaign" to "c1", "scenario" to "s1"),
            listOf(
                MeasurementMetric(10.0, Statistic.COUNT),
                MeasurementMetric(500.0, Statistic.TOTAL),
                MeasurementMetric(50.0, Statistic.MEAN),
                MeasurementMetric(80.0, Statistic.MAX),
            )
        )

        // when
        val result = converter.convert(listOf(snapshot))

        // then
        assertThat(result).hasSize(1)
        assertThat(result[0]).all {
            prop(TimescaledbMeter::type).isEqualTo("statistics")
            prop(TimescaledbMeter::count).isEqualTo(BigDecimal(10.0))
            prop(TimescaledbMeter::sum).isEqualTo(BigDecimal(500.0))
            prop(TimescaledbMeter::mean).isEqualTo(BigDecimal(50.0))
            prop(TimescaledbMeter::max).isEqualTo(BigDecimal(80.0))
            prop(TimescaledbMeter::other).isNull()
        }
    }

    @Test
    internal fun `given rate snapshot when convert then value is set`() {
        // given
        val snapshot = buildSnapshot(
            "my-rate", MeterType.RATE,
            mapOf("campaign" to "c1", "scenario" to "s1"),
            listOf(MeasurementMetric(2.5, Statistic.VALUE))
        )

        // when
        val result = converter.convert(listOf(snapshot))

        // then
        assertThat(result).hasSize(1)
        assertThat(result[0]).all {
            prop(TimescaledbMeter::type).isEqualTo("rate")
            prop(TimescaledbMeter::value).isEqualTo(BigDecimal(2.5))
            prop(TimescaledbMeter::count).isNull()
            prop(TimescaledbMeter::sum).isNull()
            prop(TimescaledbMeter::other).isNull()
        }
    }

    @Test
    internal fun `given throughput snapshot when convert then value sum mean max are set and percentiles in other`() {
        // given
        val snapshot = buildSnapshot(
            "my-throughput", MeterType.THROUGHPUT,
            mapOf("campaign" to "c1", "scenario" to "s1"),
            listOf(
                MeasurementMetric(30.0, Statistic.VALUE),
                MeasurementMetric(22.0, Statistic.MEAN),
                MeasurementMetric(173.0, Statistic.TOTAL),
                MeasurementMetric(42.0, Statistic.MAX),
                DistributionMeasurementMetric(30.0, Statistic.PERCENTILE, 50.0),
                DistributionMeasurementMetric(42.0, Statistic.PERCENTILE, 85.0),
            )
        )

        // when
        val result = converter.convert(listOf(snapshot))

        // then
        assertThat(result).hasSize(1)
        assertThat(result[0]).all {
            prop(TimescaledbMeter::type).isEqualTo("throughput")
            prop(TimescaledbMeter::value).isEqualTo(BigDecimal(30.0))
            prop(TimescaledbMeter::sum).isEqualTo(BigDecimal(173.0))
            prop(TimescaledbMeter::mean).isEqualTo(BigDecimal(22.0))
            prop(TimescaledbMeter::max).isEqualTo(BigDecimal(42.0))
            prop(TimescaledbMeter::count).isNull()
            prop(TimescaledbMeter::unit).isNull()
            prop(TimescaledbMeter::other).isNotNull()
                .isEqualTo("""{"percentile_50.0":"30","percentile_85.0":"42"}""")
        }
    }

    @Test
    internal fun `given mix of snapshots including unsupported type when convert then unsupported is skipped`() {
        // given
        val counter = buildSnapshot(
            "my-counter", MeterType.COUNTER,
            mapOf("campaign" to "c1", "scenario" to "s1"),
            listOf(MeasurementMetric(5.0, Statistic.COUNT))
        )
        val gauge = buildSnapshot(
            "my-gauge", MeterType.GAUGE,
            mapOf("campaign" to "c1", "scenario" to "s1"),
            listOf(MeasurementMetric(1.0, Statistic.VALUE))
        )
        val statistics = buildSnapshot(
            "my-statistics", MeterType.STATISTICS,
            mapOf("campaign" to "c1", "scenario" to "s1"),
            listOf(
                MeasurementMetric(10.0, Statistic.COUNT), MeasurementMetric(100.0, Statistic.TOTAL),
                MeasurementMetric(10.0, Statistic.MEAN), MeasurementMetric(20.0, Statistic.MAX)
            )
        )

        // when
        val result = converter.convert(listOf(counter, gauge, statistics))

        // then — all 3 supported types are converted
        assertThat(result).hasSize(3)
    }

    @Test
    internal fun `given empty snapshots when convert then empty list returned`() {
        assertThat(converter.convert(emptyList())).isEmpty()
    }

    @Test
    internal fun `given tags with tenant campaign and scenario when convert then they are extracted from tags`() {
        // given
        val snapshot = buildSnapshot(
            "m", MeterType.GAUGE,
            mapOf(
                "tenant" to "my-tenant",
                "campaign" to "my-campaign",
                "scenario" to "my-scenario",
                "extra" to "value"
            ),
            listOf(MeasurementMetric(1.0, Statistic.VALUE))
        )

        // when
        val result = converter.convert(listOf(snapshot))

        // then
        assertThat(result[0]).all {
            prop(TimescaledbMeter::tenant).isEqualTo("my-tenant")
            prop(TimescaledbMeter::campaign).isEqualTo("my-campaign")
            prop(TimescaledbMeter::scenario).isEqualTo("my-scenario")
            prop(TimescaledbMeter::tags).isEqualTo("""{"extra":"value"}""")
        }
    }
}
