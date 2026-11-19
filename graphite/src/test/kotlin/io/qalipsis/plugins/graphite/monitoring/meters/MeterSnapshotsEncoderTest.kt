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

package io.qalipsis.plugins.graphite.monitoring.meters

import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isTrue
import io.mockk.mockk
import io.netty.channel.ChannelHandlerContext
import io.qalipsis.api.meters.DistributionMeasurementMetric
import io.qalipsis.api.meters.Measurement
import io.qalipsis.api.meters.MeasurementMetric
import io.qalipsis.api.meters.Meter
import io.qalipsis.api.meters.MeterSnapshot
import io.qalipsis.api.meters.MeterType
import io.qalipsis.api.meters.Statistic
import io.qalipsis.plugins.graphite.client.GraphiteRecord
import io.qalipsis.plugins.graphite.monitoring.meters.catadioptre.encode
import java.time.Instant
import org.junit.jupiter.api.Test

/**
 * Regression tests for [MeterSnapshotsEncoder].
 * Graphite's plaintext protocol rejects `NaN` and `Infinity` tokens; these tests guard the
 * filter that drops non-finite measurements before they are turned into [GraphiteRecord]s.
 */
internal class MeterSnapshotsEncoderTest {

    private val encoder = MeterSnapshotsEncoder(prefix = "qalipsis.", batchSize = 10)

    private fun snapshot(type: MeterType, measurements: List<Measurement>): MeterSnapshot {
        return object : MeterSnapshot {
            override val meterId: Meter.Id = Meter.Id("my-meter", type, mapOf("scope" to "period"))
            override val timestamp: Instant = Instant.ofEpochMilli(1_000_000L)
            override val measurements: Collection<Measurement> = measurements
            override fun duplicate(meterId: Meter.Id): MeterSnapshot = this
        }
    }

    @Test
    internal fun `given timer with NaN percentiles when encode then non-finite measurements are dropped`() {
        val snapshot = snapshot(
            MeterType.TIMER,
            listOf(
                MeasurementMetric(0.0, Statistic.COUNT),
                MeasurementMetric(0.0, Statistic.MEAN),
                DistributionMeasurementMetric(Double.NaN, Statistic.PERCENTILE, 95.0),
                DistributionMeasurementMetric(Double.POSITIVE_INFINITY, Statistic.PERCENTILE, 99.0),
            )
        )
        val out = mutableListOf<Any>()

        encoder.encode(mockk<ChannelHandlerContext>(), listOf(snapshot), out)

        @Suppress("UNCHECKED_CAST")
        val records = out.single() as List<GraphiteRecord>
        assertThat(records).hasSize(2)
        records.forEach { record ->
            assertThat(record.value.toDouble().isFinite()).isTrue()
        }
    }

    @Test
    internal fun `given snapshot with only NaN measurements when encode then no record is emitted`() {
        val snapshot = snapshot(
            MeterType.TIMER,
            listOf(
                MeasurementMetric(Double.NaN, Statistic.COUNT),
                MeasurementMetric(Double.POSITIVE_INFINITY, Statistic.MEAN),
                MeasurementMetric(Double.NEGATIVE_INFINITY, Statistic.MAX),
            )
        )
        val out = mutableListOf<Any>()

        encoder.encode(mockk<ChannelHandlerContext>(), listOf(snapshot), out)

        @Suppress("UNCHECKED_CAST")
        val records = out.single() as List<GraphiteRecord>
        assertThat(records).isEmpty()
    }

    @Test
    internal fun `given all finite measurements when encode then all records are kept`() {
        val snapshot = snapshot(
            MeterType.TIMER,
            listOf(
                MeasurementMetric(1.0, Statistic.COUNT),
                MeasurementMetric(2.5, Statistic.MEAN),
                DistributionMeasurementMetric(9.5, Statistic.PERCENTILE, 99.0),
            )
        )
        val out = mutableListOf<Any>()

        encoder.encode(mockk<ChannelHandlerContext>(), listOf(snapshot), out)

        @Suppress("UNCHECKED_CAST")
        val records = out.single() as List<GraphiteRecord>
        assertThat(records).hasSize(3)
    }
}
