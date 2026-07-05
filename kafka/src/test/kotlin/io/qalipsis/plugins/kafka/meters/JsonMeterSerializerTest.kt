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

package io.qalipsis.plugins.kafka.meters

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.doesNotContain
import io.mockk.every
import io.mockk.mockk
import io.qalipsis.api.meters.DistributionMeasurementMetric
import io.qalipsis.api.meters.Measurement
import io.qalipsis.api.meters.MeasurementMetric
import io.qalipsis.api.meters.Meter
import io.qalipsis.api.meters.MeterType
import io.qalipsis.api.meters.Statistic
import java.time.Instant
import org.junit.jupiter.api.Test

/**
 * Regression tests for [JsonMeterSerializer].
 * Raw `NaN` / `Infinity` tokens produce invalid JSON and break downstream Kafka consumers.
 * These tests guard the filter that drops non-finite measurements for timer, summary and throughput.
 */
internal class JsonMeterSerializerTest {

    private val serializer = JsonMeterSerializer(timestampFieldName = "@timestamp")

    private fun snapshot(
        type: MeterType,
        measurements: List<Measurement>,
    ): io.qalipsis.api.meters.MeterSnapshot {
        val snapshot = mockk<io.qalipsis.api.meters.MeterSnapshot>()
        every { snapshot.timestamp } returns Instant.ofEpochMilli(1_000_000L)
        every { snapshot.meterId } returns Meter.Id("my-meter", type, mapOf("scope" to "period"))
        every { snapshot.measurements } returns measurements
        return snapshot
    }

    @Test
    internal fun `given timer with NaN percentiles when serialize then non-finite fields are dropped`() {
        val snapshot = snapshot(
            MeterType.TIMER,
            listOf(
                MeasurementMetric(0.0, Statistic.COUNT),
                MeasurementMetric(0.0, Statistic.MEAN),
                MeasurementMetric(0.0, Statistic.TOTAL_TIME),
                DistributionMeasurementMetric(Double.NaN, Statistic.PERCENTILE, 95.0),
                DistributionMeasurementMetric(Double.POSITIVE_INFINITY, Statistic.PERCENTILE, 99.0),
            )
        )

        val json = String(serializer.serialize("topic", snapshot))

        assertThat(json).doesNotContain("NaN")
        assertThat(json).doesNotContain("Infinity")
        assertThat(json).contains("\"count\":0.0")
        assertThat(json).contains("\"mean\":0.0")
    }

    @Test
    internal fun `given summary with NaN measurements when serialize then non-finite fields are dropped`() {
        val snapshot = snapshot(
            MeterType.DISTRIBUTION_SUMMARY,
            listOf(
                MeasurementMetric(1.0, Statistic.COUNT),
                MeasurementMetric(Double.NaN, Statistic.MEAN),
                DistributionMeasurementMetric(Double.NaN, Statistic.PERCENTILE, 50.0),
            )
        )

        val json = String(serializer.serialize("topic", snapshot))

        assertThat(json).doesNotContain("NaN")
        assertThat(json).contains("\"count\":1.0")
    }

    @Test
    internal fun `given throughput with NaN measurements when serialize then non-finite fields are dropped`() {
        val snapshot = snapshot(
            MeterType.THROUGHPUT,
            listOf(
                MeasurementMetric(2.0, Statistic.VALUE),
                MeasurementMetric(Double.POSITIVE_INFINITY, Statistic.MAX),
                DistributionMeasurementMetric(Double.NaN, Statistic.PERCENTILE, 99.0),
            )
        )

        val json = String(serializer.serialize("topic", snapshot))

        assertThat(json).doesNotContain("NaN")
        assertThat(json).doesNotContain("Infinity")
        assertThat(json).contains("\"value\":2.0")
    }

    @Test
    internal fun `given timer with all finite values when serialize then all fields are kept`() {
        val snapshot = snapshot(
            MeterType.TIMER,
            listOf(
                MeasurementMetric(1.0, Statistic.COUNT),
                MeasurementMetric(2.5, Statistic.MEAN),
                DistributionMeasurementMetric(9.5, Statistic.PERCENTILE, 99.0),
            )
        )

        val json = String(serializer.serialize("topic", snapshot))

        assertThat(json).doesNotContain("NaN")
        assertThat(json).contains("\"count\":1.0")
        assertThat(json).contains("\"mean\":2.5")
        assertThat(json).contains("\"percentile_99_0\":9.5")
    }
}
