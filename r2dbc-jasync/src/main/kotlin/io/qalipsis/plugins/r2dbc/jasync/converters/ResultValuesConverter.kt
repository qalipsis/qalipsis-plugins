/*
 * Copyright 2022 AERIS IT Solutions GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing
 * permissions and limitations under the License.
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
