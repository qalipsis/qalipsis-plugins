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
 * R2DBC returns Java 8 types natively, so the converter is an identity function.
 */
internal class ResultValuesConverterTest {

    val converter = ResultValuesConverter()

    @Test
    internal fun `should keep local date unchanged`() {
        val java8 = LocalDate.of(2020, 11, 6)
        val result = converter.process(java8)
        assertThat(result).isEqualTo(java8)
    }

    @Test
    internal fun `should keep local time unchanged`() {
        val java8 = LocalTime.of(11, 23, 56, 123_000_000)
        val result = converter.process(java8)
        assertThat(result).isEqualTo(java8)
    }

    @Test
    internal fun `should keep local date and time unchanged`() {
        val java8 = LocalDateTime.of(2020, 11, 6, 11, 23, 56, 123_000_000)
        val result = converter.process(java8)
        assertThat(result).isEqualTo(java8)
    }

    @Test
    internal fun `should keep date and time with offset unchanged`() {
        val java8 = ZonedDateTime.of(2020, 11, 6, 11, 23, 56, 123_000_000, ZoneId.ofOffset("", ZoneOffset.ofHours(1)))
        val result = converter.process(java8)
        assertThat(result).isEqualTo(java8)
    }

    @Test
    internal fun `should keep date and time with zone ID unchanged`() {
        val java8 = ZonedDateTime.of(2020, 11, 6, 11, 23, 56, 123_000_000, ZoneId.of("CET"))
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
