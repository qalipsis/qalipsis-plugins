/*
 * QALIPSIS
 * Copyright (C) 2025 AERIS IT Solutions GmbH
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

import com.google.protobuf.Timestamp
import io.qalipsis.api.meters.DistributionMeasurementMetric
import io.qalipsis.api.meters.MeterSnapshot
import io.qalipsis.api.meters.MeterType
import io.qalipsis.api.meters.Statistic
import io.qalipsis.api.meters.UnsupportedMeterException
import io.qalipsis.plugins.kafka.meters.MeterKt.percentile
import java.time.Instant
import java.util.concurrent.TimeUnit
import org.apache.commons.text.StringEscapeUtils

/**
 * Converts a [MeterSnapshot] to a protobuf serializable [MeterOuterClass.Meter] class.
 */
internal class ProtobufMeterConverter {

    fun convert(meterSnapshot: MeterSnapshot): MeterOuterClass.Meter {
        val protobufMeter = MeterOuterClass.Meter.newBuilder()
        protobufMeter.name = meterSnapshot.meterId.meterName
        protobufMeter.type = meterSnapshot.meterId.type.value
        protobufMeter.timestamp = instantToProtobufTimestamp(meterSnapshot.timestamp)

        meterSnapshot.meterId.tags.mapNotNull { (key, value) ->
            when (key) {
                "tenant" -> protobufMeter.tenant = value
                "campaign" -> protobufMeter.campaign = value
                "scenario" -> protobufMeter.scenario = value
                "step" -> protobufMeter.step = value
                else -> protobufMeter.tagBuilder.putTags(
                    StringEscapeUtils.escapeJson(key).lowercase(),
                    StringEscapeUtils.escapeJson(value)
                )
            }
        }

        when (meterSnapshot.meterId.type) {
            MeterType.GAUGE -> convertGauge(meterSnapshot, protobufMeter)
            MeterType.COUNTER -> convertCounter(meterSnapshot, protobufMeter)
            MeterType.TIMER -> convertTimer(meterSnapshot, protobufMeter)
            MeterType.DISTRIBUTION_SUMMARY -> convertSummary(meterSnapshot, protobufMeter)
            MeterType.RATE -> convertRate(meterSnapshot, protobufMeter)
            MeterType.THROUGHPUT -> convertThroughput(meterSnapshot, protobufMeter)
            else -> throw UnsupportedMeterException("Meter ${meterSnapshot.meterId} not supported")
        }

        return protobufMeter.build()
    }

    private fun convertCounter(counterSnapshot: MeterSnapshot, meter: MeterOuterClass.Meter.Builder) {
        val value = counterSnapshot.measurements.first { it.statistic == Statistic.COUNT }.value
        if (value.isFinite()) {
            meter.count = value.toLong()
        }
    }

    private fun convertGauge(gaugeSnapshot: MeterSnapshot, meter: MeterOuterClass.Meter.Builder) {
        val value = gaugeSnapshot.measurements.first { it.statistic == Statistic.VALUE }.value
        if (value.isFinite()) {
            meter.value = value
        }
    }

    private fun convertTimer(timerSnapshot: MeterSnapshot, meter: MeterOuterClass.Meter.Builder) {
        meter.unit = TimeUnit.MICROSECONDS.name
        // Non-finite measurements (NaN / Infinity) are skipped: protobuf's JSON printer rejects them
        // and consumers of the message would fail downstream.
        timerSnapshot.measurements.filter { it.value.isFinite() }.forEach {
            when (it.statistic) {
                Statistic.COUNT -> meter.count = it.value.toLong()
                Statistic.TOTAL_TIME -> meter.sum = it.value
                Statistic.MEAN -> meter.mean = it.value
                Statistic.MAX -> meter.max = it.value
                Statistic.PERCENTILE -> {
                    it as DistributionMeasurementMetric
                    meter.addPercentiles(percentile {
                        this.percentage = it.observationPoint
                        this.value = it.value
                    })
                }

                else -> Unit
            }
        }
    }

    private fun convertSummary(summarySnapshot: MeterSnapshot, meter: MeterOuterClass.Meter.Builder) {
        // Non-finite measurements (NaN / Infinity) are skipped: protobuf's JSON printer rejects them
        // and consumers of the message would fail downstream.
        summarySnapshot.measurements.filter { it.value.isFinite() }.forEach {
            when (it.statistic) {
                Statistic.COUNT -> meter.count = it.value.toLong()
                Statistic.TOTAL -> meter.sum = it.value
                Statistic.MEAN -> meter.mean = it.value
                Statistic.MAX -> meter.max = it.value
                Statistic.PERCENTILE -> {
                    it as DistributionMeasurementMetric
                    meter.addPercentiles(percentile {
                        this.percentage = it.observationPoint
                        this.value = it.value
                    })
                }

                else -> Unit
            }
        }
    }

    private fun convertRate(rateSnapshot: MeterSnapshot, meter: MeterOuterClass.Meter.Builder) {
        val value = rateSnapshot.measurements.first { it.statistic == Statistic.VALUE }.value
        if (value.isFinite()) {
            meter.value = value
        }
    }

    private fun convertThroughput(throughputSnapshot: MeterSnapshot, meter: MeterOuterClass.Meter.Builder) {
        // Non-finite measurements (NaN / Infinity) are skipped: protobuf's JSON printer rejects them
        // and consumers of the message would fail downstream.
        throughputSnapshot.measurements.filter { it.value.isFinite() }.forEach {
            when (it.statistic) {
                Statistic.VALUE -> meter.value = it.value
                Statistic.TOTAL -> meter.sum = it.value
                Statistic.MEAN -> meter.mean = it.value
                Statistic.MAX -> meter.max = it.value
                Statistic.PERCENTILE -> {
                    it as DistributionMeasurementMetric
                    meter.addPercentiles(percentile {
                        this.percentage = it.observationPoint
                        this.value = it.value
                    })
                }

                else -> Unit
            }
        }
    }

    fun instantToProtobufTimestamp(instant: Instant): Timestamp {
        return Timestamp.newBuilder().setSeconds(instant.epochSecond).setNanos(instant.nano).build()
    }

}
