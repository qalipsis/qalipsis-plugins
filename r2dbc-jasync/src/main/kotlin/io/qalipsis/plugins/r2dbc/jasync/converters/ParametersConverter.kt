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
