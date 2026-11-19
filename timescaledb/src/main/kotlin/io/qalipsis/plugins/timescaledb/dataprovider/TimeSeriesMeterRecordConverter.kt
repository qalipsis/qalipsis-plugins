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

package io.qalipsis.plugins.timescaledb.dataprovider

import com.fasterxml.jackson.databind.ObjectMapper
import io.qalipsis.api.meters.MeterType
import io.qalipsis.api.report.TimeSeriesMeter
import io.qalipsis.api.report.TimeSeriesRecord
import io.r2dbc.spi.Row
import io.r2dbc.spi.RowMetadata
import jakarta.inject.Singleton
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeUnit

/**
 * Converter from SQL result of time-series records to [io.qalipsis.api.report.TimeSeriesMeter].
 *
 * @author Eric Jessé
 */
@Suppress("UNCHECKED_CAST")
@Singleton
internal class TimeSeriesMeterRecordConverter(
    private val objectMapper: ObjectMapper
) : TimeSeriesRecordConverter {

    override fun convert(row: Row, metadata: RowMetadata): TimeSeriesRecord {
        return when (val type = row.get("type", String::class.java)!!) {
            MeterType.TIMER.value.lowercase() -> convertTimer(type, row)
            else -> convertNonTimer(type, row)
        }
    }

    private fun convertTimer(type: String, row: Row): TimeSeriesMeter {
        val unit = row.get("unit", String::class.java)
            ?.let { runCatching { TimeUnit.valueOf(it) }.getOrNull() }
            ?: DEFAULT_TIMER_UNIT
        val other = readOther(row)
        return TimeSeriesMeter(
            name = row.get("name", String::class.java)!!,
            type = type,
            timestamp = row.get("timestamp", Instant::class.java)!!,
            campaign = row.get("campaign", String::class.java),
            scenario = row.get("scenario", String::class.java),
            tags = readTags(row),
            count = row.get("count", BigDecimal::class.java)?.toLong(),
            sumDuration = row.get("sum", BigDecimal::class.java)?.toDuration(unit),
            maxDuration = row.get("max", BigDecimal::class.java)?.toDuration(unit),
            meanDuration = row.get("mean", BigDecimal::class.java)?.toDuration(unit),
            percentileDurations = other
                ?.filterKeys { it.startsWith(PERCENTILE_PREFIX) }
                ?.map { (key, value) -> key.removePrefix(PERCENTILE_PREFIX) to value.toDuration(unit) }
                ?.toMap()
                ?.takeIf { it.isNotEmpty() },
            other = other?.filterKeys { !it.startsWith(PERCENTILE_PREFIX) }?.takeIf { it.isNotEmpty() }
        )
    }

    private fun convertNonTimer(type: String, row: Row): TimeSeriesMeter {
        return TimeSeriesMeter(
            name = row.get("name", String::class.java)!!,
            type = type,
            timestamp = row.get("timestamp", Instant::class.java)!!,
            campaign = row.get("campaign", String::class.java),
            scenario = row.get("scenario", String::class.java),
            tags = readTags(row),
            count = row.get("count", BigDecimal::class.java)?.toLong(),
            sum = row.get("sum", BigDecimal::class.java),
            mean = row.get("mean", BigDecimal::class.java),
            max = row.get("max", BigDecimal::class.java),
            value = row.get("value", BigDecimal::class.java),
            other = readOther(row)
        )
    }

    private fun readTags(row: Row) = row.get("tags", String::class.java)
        ?.let { objectMapper.readValue(it, Map::class.java) } as Map<String, String>?

    private fun readOther(row: Row) = (row.get("other", String::class.java)
        ?.let { objectMapper.readValue(it, Map::class.java) } as Map<String, String>?)
        ?.mapValues { BigDecimal(it.value) }

    /**
     * Converts a value expressed in [unit] into a [Duration], keeping the sub-unit precision down to the nanosecond.
     */
    private fun BigDecimal.toDuration(unit: TimeUnit): Duration =
        Duration.ofNanos(multiply(BigDecimal.valueOf(unit.toNanos(1))).setScale(0, RoundingMode.HALF_UP).toLong())

    private companion object {

        /**
         * Unit of the timer values when the record does not specify any.
         */
        val DEFAULT_TIMER_UNIT = TimeUnit.MILLISECONDS

        const val PERCENTILE_PREFIX = "percentile_"
    }
}