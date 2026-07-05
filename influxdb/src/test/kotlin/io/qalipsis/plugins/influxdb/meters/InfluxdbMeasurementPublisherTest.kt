/*
 * QALIPSIS
 * Copyright (C) 2026 AERIS IT Solutions GmbH
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

package io.qalipsis.plugins.influxdb.meters

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.doesNotContain
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import io.aerisconsulting.catadioptre.invokeInvisible
import io.mockk.every
import io.mockk.mockk
import io.qalipsis.api.meters.DistributionMeasurementMetric
import io.qalipsis.api.meters.Measurement
import io.qalipsis.api.meters.MeasurementMetric
import io.qalipsis.api.meters.Meter
import io.qalipsis.api.meters.MeterSnapshot
import io.qalipsis.api.meters.MeterType
import io.qalipsis.api.meters.Statistic
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.jupiter.api.Test

/**
 * Regression tests for [InfluxdbMeasurementPublisher.createRecord].
 * The InfluxDB line protocol rejects `NaN` and `Infinity` tokens with HTTP 400 "invalid number".
 * These tests guard the filter that drops non-finite measurements.
 */
internal class InfluxdbMeasurementPublisherTest {

    private val publisher =
        InfluxdbMeasurementPublisher(InfluxDbMeasurementConfiguration(), CoroutineScope(Dispatchers.Unconfined))

    private fun snapshot(type: MeterType, measurements: List<Measurement>): MeterSnapshot {
        val snapshot = mockk<MeterSnapshot>()
        every { snapshot.timestamp } returns Instant.ofEpochMilli(1_000_000L)
        every { snapshot.meterId } returns Meter.Id("my-timer", type, mapOf("scope" to "period"))
        every { snapshot.measurements } returns measurements
        return snapshot
    }

    @Test
    internal fun `given timer with NaN percentiles when createRecord then non-finite fields are dropped`() {
        val snapshot = snapshot(
            MeterType.TIMER,
            listOf(
                MeasurementMetric(0.0, Statistic.COUNT),
                MeasurementMetric(0.0, Statistic.TOTAL_TIME),
                MeasurementMetric(0.0, Statistic.MEAN),
                MeasurementMetric(0.0, Statistic.MAX),
                DistributionMeasurementMetric(Double.NaN, Statistic.PERCENTILE, 95.0),
                DistributionMeasurementMetric(Double.NaN, Statistic.PERCENTILE, 99.0),
            )
        )

        val record: String? = publisher.invokeInvisible("createRecord", snapshot)

        assertThat(record).isNotNull().doesNotContain("NaN")
        assertThat(record!!).contains("count=0.0")
        assertThat(record).contains("mean=0.0")
    }

    @Test
    internal fun `given snapshot with only NaN measurements when createRecord then returns null`() {
        val snapshot = snapshot(
            MeterType.TIMER,
            listOf(
                MeasurementMetric(Double.NaN, Statistic.COUNT),
                MeasurementMetric(Double.POSITIVE_INFINITY, Statistic.MEAN),
                MeasurementMetric(Double.NEGATIVE_INFINITY, Statistic.MAX),
            )
        )

        val record: String? = publisher.invokeInvisible("createRecord", snapshot)

        assertThat(record).isNull()
    }

    @Test
    internal fun `given fully finite measurements when createRecord then all fields are kept`() {
        val snapshot = snapshot(
            MeterType.TIMER,
            listOf(
                MeasurementMetric(1.0, Statistic.COUNT),
                MeasurementMetric(2.5, Statistic.MEAN),
                DistributionMeasurementMetric(9.5, Statistic.PERCENTILE, 99.0),
            )
        )

        val record: String? = publisher.invokeInvisible("createRecord", snapshot)

        assertThat(record).isNotNull().doesNotContain("NaN")
        assertThat(record!!).contains("count=1.0")
        assertThat(record).contains("mean=2.5")
        assertThat(record).contains("percentile_99.0=9.5")
    }
}
