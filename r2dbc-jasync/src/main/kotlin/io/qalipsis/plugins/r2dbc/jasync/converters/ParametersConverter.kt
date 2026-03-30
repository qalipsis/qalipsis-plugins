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

package io.qalipsis.plugins.r2dbc.jasync.converters

import io.qalipsis.api.annotations.PluginComponent
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/**
 * Converter of Java 8 time into Joda types equivalent, used by JAsync.
 */
@PluginComponent
internal class ParametersConverter {

    fun process(value: Any?): Any? {
        return when (value) {
            is LocalDate -> org.joda.time.LocalDate(value.year, value.monthValue, value.dayOfMonth)
            is LocalTime -> org.joda.time.LocalTime(
                value.hour,
                value.minute,
                value.second,
                TimeUnit.NANOSECONDS.toMillis(value.nano.toLong()).toInt()
            )
            is LocalDateTime -> org.joda.time.LocalDateTime(
                    value.year,
                    value.monthValue,
                    value.dayOfMonth,
                    value.hour,
                    value.minute,
                    value.second,
                    TimeUnit.NANOSECONDS.toMillis(value.nano.toLong()).toInt()
            )
            is ZonedDateTime -> org.joda.time.DateTime(value.toInstant().toEpochMilli())
            is Instant -> org.joda.time.Instant(value.toEpochMilli())
            is Duration -> org.joda.time.Duration(value.toMillis())
            else -> value
        }
    }

}
