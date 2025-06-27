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
 * Converter of Joda types used by JAsync into Java 8 time equivalent.
 */
@PluginComponent
internal class ResultValuesConverter {

    fun process(value: Any?): Any? {
        return when (value) {
            is org.joda.time.LocalDate -> LocalDate.of(value.year, value.monthOfYear, value.dayOfMonth)
            is org.joda.time.LocalTime -> LocalTime.of(
                value.hourOfDay, value.minuteOfHour, value.secondOfMinute,
                TimeUnit.MILLISECONDS.toNanos(value.millisOfSecond.toLong()).toInt()
            )
            is org.joda.time.LocalDateTime -> LocalDateTime.of(
                value.year, value.monthOfYear, value.dayOfMonth,
                    value.hourOfDay, value.minuteOfHour, value.secondOfMinute,
                    TimeUnit.MILLISECONDS.toNanos(value.millisOfSecond.toLong()).toInt()
            )
            is org.joda.time.DateTime -> ZonedDateTime.ofInstant(
                    Instant.ofEpochMilli(value.millis), java.time.ZoneId.of(value.zone.id)
            )
            is org.joda.time.Instant -> Instant.ofEpochMilli(value.millis)
            is org.joda.time.Duration -> Duration.ofMillis(value.millis)
            else -> value
        }
    }

}
