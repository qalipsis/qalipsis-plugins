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

package io.qalipsis.plugins.sql.converters

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import assertk.assertions.isSameInstanceAs
import io.qalipsis.test.mockk.relaxedMockk
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import org.junit.jupiter.api.Test

/**
 * R2DBC drivers accept Java 8 time types natively, so the converter is an identity function.
 *
 * @author Eric Jessé
 */
internal class ParametersConverterTest {

    val converter = ParametersConverter()

    @Test
    internal fun `should keep local date unchanged`() {
        val java8 = LocalDate.of(2020, 11, 6)
        val result = converter.process(java8)
        assertThat(result).isEqualTo(java8)
    }

    @Test
    internal fun `should keep local time unchanged`() {
        val java8 = LocalTime.of(11, 23, 56, 123_213_797)
        val result = converter.process(java8)
        assertThat(result).isEqualTo(java8)
    }

    @Test
    internal fun `should keep local date and time unchanged`() {
        val java8 = LocalDateTime.of(2020, 11, 6, 11, 23, 56, 123_213_797)
        val result = converter.process(java8)
        assertThat(result).isEqualTo(java8)
    }

    @Test
    internal fun `should keep date and time with offset unchanged`() {
        val java8 = ZonedDateTime.of(2020, 11, 6, 11, 23, 56, 123_213_797, ZoneId.ofOffset("", ZoneOffset.ofHours(1)))
        val result = converter.process(java8)
        assertThat(result).isEqualTo(java8)
    }

    @Test
    internal fun `should keep date and time with zone ID unchanged`() {
        val java8 = ZonedDateTime.of(2020, 11, 6, 11, 23, 56, 123_213_797, ZoneId.of("CET"))
        val result = converter.process(java8)
        assertThat(result).isEqualTo(java8)
    }

    @Test
    internal fun `should keep instant unchanged`() {
        val java8 = Instant.ofEpochMilli(1604658236123)
        val result = converter.process(java8)
        assertThat(result).isEqualTo(java8)
    }

    @Test
    internal fun `should keep duration unchanged`() {
        val java8 = Duration.ofMillis(1604658236123)
        val result = converter.process(java8)
        assertThat(result).isEqualTo(java8)
    }

    @Test
    internal fun `should keep null value`() {
        assertThat(converter.process(null)).isNull()
    }

    @Test
    internal fun `should keep non-standard value unchanged`() {
        val value = relaxedMockk<Any>()
        val result = converter.process(value)
        assertThat(result).isSameInstanceAs(value)
    }

}
