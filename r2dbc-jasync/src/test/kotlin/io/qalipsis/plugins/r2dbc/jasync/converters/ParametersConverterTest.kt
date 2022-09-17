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

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isSameAs
import io.qalipsis.test.mockk.relaxedMockk
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime

/**
 *
 * @author Eric Jessé
 */
internal class ParametersConverterTest {

    val javaToJodaConverter = ParametersConverter()

    @Test
    internal fun `should convert local date`() {
        val java8 = LocalDate.of(2020, 11, 6)
        val joda = javaToJodaConverter.process(java8)
        assertThat(joda).isEqualTo(org.joda.time.LocalDate(2020, 11, 6))
    }

    @Test
    internal fun `should convert local time`() {
        val java8 = LocalTime.of(11, 23, 56, 123_213_797)
        val joda = javaToJodaConverter.process(java8)
        assertThat(joda).isEqualTo(org.joda.time.LocalTime(11, 23, 56, 123))
    }

    @Test
    internal fun `should convert local date and time`() {
        val java8 = LocalDateTime.of(2020, 11, 6, 11, 23, 56, 123_213_797)
        val joda = javaToJodaConverter.process(java8)
        assertThat(joda).isEqualTo(org.joda.time.LocalDateTime(2020, 11, 6, 11, 23, 56, 123))
    }

    @Test
    internal fun `should convert date and time with offset`() {
        val java8 = ZonedDateTime.of(2020, 11, 6, 11, 23, 56, 123_213_797, ZoneId.ofOffset("", ZoneOffset.ofHours(1)))
        val joda = javaToJodaConverter.process(java8)
        assertThat(joda).isNotNull().isInstanceOf(org.joda.time.DateTime::class)
            .transform { it.millis }.isEqualTo(org.joda.time.DateTime.parse("2020-11-06T11:23:56.123+01:00").millis)
    }

    @Test
    internal fun `should convert date and time with zone ID`() {
        val java8 = ZonedDateTime.of(2020, 11, 6, 11, 23, 56, 123_213_797, ZoneId.of("CET"))
        val joda = javaToJodaConverter.process(java8)
        assertThat(joda).isNotNull().isInstanceOf(org.joda.time.DateTime::class)
            .transform { it.millis }.isEqualTo(org.joda.time.DateTime.parse("2020-11-06T11:23:56.123+01:00").millis)
    }

    @Test
    internal fun `should convert instant`() {
        val java8 = Instant.ofEpochMilli(1604658236123)
        val joda = javaToJodaConverter.process(java8)
        assertThat(joda).isEqualTo(org.joda.time.Instant(1604658236123))
    }

    @Test
    internal fun `should convert duration`() {
        val java8 = Duration.ofMillis(1604658236123)
        val joda = javaToJodaConverter.process(java8)
        assertThat(joda).isEqualTo(org.joda.time.Duration(1604658236123))
    }

    @Test
    internal fun `should keep null value`() {
        assertThat(javaToJodaConverter.process(null)).isNull()
    }

    @Test
    internal fun `should keep non joda value unchanged`() {
        val java8 = relaxedMockk<Any>()
        val joda = javaToJodaConverter.process(java8)
        assertThat(joda).isSameAs(joda)
    }

}