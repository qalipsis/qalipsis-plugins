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
