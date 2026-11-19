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

package io.qalipsis.plugins.elasticsearch.monitoring.meters

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
 * Regression tests for [ElasticsearchMeasurementPublisher.metersToJsonConverter].
 * Elasticsearch's bulk API rejects `NaN` and `Infinity` as invalid JSON numbers.
 * These tests guard the filter that drops non-finite measurements.
 */
internal class ElasticsearchMeasurementPublisherTest {

    private val publisher =
        ElasticsearchMeasurementPublisher(
            CoroutineScope(Dispatchers.Unconfined),
            ElasticsearchMeasurementConfiguration()
        )

    private fun snapshot(type: MeterType, measurements: List<Measurement>): MeterSnapshot {
        val snapshot = mockk<MeterSnapshot>()
        every { snapshot.timestamp } returns Instant.ofEpochMilli(1_000_000L)
        every { snapshot.meterId } returns Meter.Id("my-meter", type, mapOf("scope" to "period"))
        every { snapshot.measurements } returns measurements
        return snapshot
    }

    @Test
    internal fun `given timer with NaN percentiles when metersToJsonConverter then non-finite fields are dropped`() {
        val snapshot = snapshot(
            MeterType.TIMER,
            listOf(
                MeasurementMetric(0.0, Statistic.COUNT),
                MeasurementMetric(0.0, Statistic.MEAN),
                DistributionMeasurementMetric(Double.NaN, Statistic.PERCENTILE, 95.0),
                DistributionMeasurementMetric(Double.POSITIVE_INFINITY, Statistic.PERCENTILE, 99.0),
            )
        )

        val result: Pair<String, String>? = publisher.invokeInvisible("metersToJsonConverter", snapshot)

        assertThat(result).isNotNull()
        val json = result!!.second
        assertThat(json).doesNotContain("NaN")
        assertThat(json).doesNotContain("Infinity")
        assertThat(json).contains("\"count\":0.0")
        assertThat(json).contains("\"mean\":0.0")
    }

    @Test
    internal fun `given snapshot with only NaN measurements when metersToJsonConverter then returns null`() {
        val snapshot = snapshot(
            MeterType.TIMER,
            listOf(
                MeasurementMetric(Double.NaN, Statistic.COUNT),
                MeasurementMetric(Double.POSITIVE_INFINITY, Statistic.MEAN),
                MeasurementMetric(Double.NEGATIVE_INFINITY, Statistic.MAX),
            )
        )

        val result: Pair<String, String>? = publisher.invokeInvisible("metersToJsonConverter", snapshot)

        assertThat(result).isNull()
    }

    @Test
    internal fun `given fully finite measurements when metersToJsonConverter then all metrics are kept`() {
        val snapshot = snapshot(
            MeterType.TIMER,
            listOf(
                MeasurementMetric(1.0, Statistic.COUNT),
                MeasurementMetric(2.5, Statistic.MEAN),
                DistributionMeasurementMetric(9.5, Statistic.PERCENTILE, 99.0),
            )
        )

        val result: Pair<String, String>? = publisher.invokeInvisible("metersToJsonConverter", snapshot)

        assertThat(result).isNotNull()
        val json = result!!.second
        assertThat(json).doesNotContain("NaN")
        assertThat(json).contains("\"count\":1.0")
        assertThat(json).contains("\"mean\":2.5")
        assertThat(json).contains("\"percentile_99\":9.5")
    }
}
