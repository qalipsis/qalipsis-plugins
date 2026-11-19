/*
 * QALIPSIS
 * Copyright (C) 2026 AERIS IT Solutions GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
