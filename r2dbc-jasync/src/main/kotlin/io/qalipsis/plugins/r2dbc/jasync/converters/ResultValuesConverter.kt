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
