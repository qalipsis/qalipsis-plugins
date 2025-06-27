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

package io.qalipsis.plugins.influxdb.poll

import assertk.all
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import com.influxdb.query.FluxRecord
import io.qalipsis.test.assertk.prop
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Instant

internal class InfluxDbPollStatementTest {

    @Nested
    inner class SortWithTime {

        @Test
        fun `should return the original query when no tie-breaker is known`() {
            // given
            val query = """from(bucket: "the-bucket") |> filter(fn: (r) => (r["_measurement"] == "temperature"))
  |> range(start: 2018-05-22T23:30:00Z, stop: 2018-05-23T00:00:00Z)
  |> sort(columns: ["tag", "_value"], desc: true)"""

            // when
            val statement = InfluxDbPollStatement(query)

            // then
            assertThat(statement.getNextQuery().query).isEqualTo(query)
        }

        @Test
        internal fun `should enhance the query with default sorting`() {
            // given
            val query = """from(bucket: "the-bucket") |> filter(fn: (r) => (r["_measurement"] == "temperature"))"""

            // when
            val statement = InfluxDbPollStatement(query)

            // then
            assertThat(statement).all {
                prop("tieBreakerField").isEqualTo("_time")
                prop("comparatorClause").isEqualTo(">=")
                prop("rangeStartStatementRange").isNull()
                prop("filterStatementEndRange").isNull()
            }

            // when
            val timestamp = Instant.now()
            statement.saveTiebreaker(listOf(FluxRecord(1).apply {
                values["_time"] = timestamp
                values["_value"] = 5432
                values["tag"] = "this is a value"
            }))

            // then
            val expected =
                "from(bucket: \"the-bucket\") |> filter(fn: (r) => (r[\"_measurement\"] == \"temperature\")) |> range(start: $timestamp)"
            assertThat(statement.getNextQuery().query).isEqualTo(expected)

            // when
            statement.reset()

            // then
            assertThat(statement.getNextQuery().query).isEqualTo(query)
        }

        @Test
        internal fun `should enhance the query with range and default sorting`() {
            // given
            val query =
                """from(bucket: "the-bucket") 
    |> range(start: 2018-05-22T23:30:00Z, stop: 2018-05-23T00:00:00Z) 
    |> filter(fn: (r) => (r["_measurement"] == "temperature"))"""

            // when
            val statement = InfluxDbPollStatement(query)

            // then
            assertThat(statement).all {
                prop("tieBreakerField").isEqualTo("_time")
                prop("comparatorClause").isEqualTo(">=")
                prop("rangeStartStatementRange").isEqualTo(41 until 68)
                prop("filterStatementEndRange").isNull()
            }

            // when
            val timestamp = Instant.now()
            statement.saveTiebreaker(listOf(FluxRecord(1).apply {
                values["_time"] = timestamp
                values["_value"] = 5432
                values["tag"] = "this is a value"
            }))

            // then
            val expected = """from(bucket: "the-bucket") 
    |> range(start: $timestamp, stop: 2018-05-23T00:00:00Z) 
    |> filter(fn: (r) => (r["_measurement"] == "temperature"))"""
            assertThat(statement.getNextQuery().query).isEqualTo(expected)

            // when
            statement.reset()

            // then
            assertThat(statement.getNextQuery().query).isEqualTo(query)
        }
    }

    @Nested
    inner class SortWithValue {

        @Test
        internal fun `should enhance the query with default value sorting and no existing filter`() {
            // given
            val query = """from(bucket: "the-bucket") 
  |> range(start: 2018-05-22T23:30:00Z, stop: 2018-05-23T00:00:00Z)
  |> sort()"""

            // when
            val statement = InfluxDbPollStatement(query)

            // then
            assertThat(statement).all {
                prop("tieBreakerField").isEqualTo("_value")
                prop("comparatorClause").isEqualTo(">=")
                prop("rangeStartStatementRange").isNull()
                prop("filterStatementEndRange").isNull()
            }

            // when
            val timestamp = Instant.now()
            statement.saveTiebreaker(listOf(FluxRecord(1).apply {
                values["_time"] = timestamp
                values["_value"] = 5432
                values["tag"] = "this is a value"
            }))

            // then
            val expected = """from(bucket: "the-bucket") 
  |> range(start: 2018-05-22T23:30:00Z, stop: 2018-05-23T00:00:00Z)
  |> sort() |> filter(fn: (r) => (r._value >= 5432))"""
            assertThat(statement.getNextQuery().query).isEqualTo(expected)

            // when
            statement.reset()

            // then
            assertThat(statement.getNextQuery().query).isEqualTo(query)
        }

        @Test
        internal fun `should enhance the query with default value sorting and an existing filter`() {
            // given
            val query = """from(bucket: "the-bucket") 
  |> filter(fn: (r) => (r["_measurement"] == "temperature"))
  |> range(start: 2018-05-22T23:30:00Z, stop: 2018-05-23T00:00:00Z)
  |> sort()"""

            // when
            val statement = InfluxDbPollStatement(query)

            // then
            assertThat(statement).all {
                prop("tieBreakerField").isEqualTo("_value")
                prop("comparatorClause").isEqualTo(">=")
                prop("rangeStartStatementRange").isNull()
                prop("filterStatementEndRange").isEqualTo(51..51)
            }

            // when
            val timestamp = Instant.now()
            statement.saveTiebreaker(listOf(FluxRecord(1).apply {
                values["_time"] = timestamp
                values["_value"] = 5432
                values["tag"] = "this is a value"
            }))

            // then
            val expected = """from(bucket: "the-bucket") 
  |> filter(fn: (r) => (r._value >= 5432 and r["_measurement"] == "temperature"))
  |> range(start: 2018-05-22T23:30:00Z, stop: 2018-05-23T00:00:00Z)
  |> sort()"""
            assertThat(statement.getNextQuery().query).isEqualTo(expected)

            // when
            statement.reset()

            // then
            assertThat(statement.getNextQuery().query).isEqualTo(query)
        }

        @Test
        internal fun `should enhance the query with ascending value sorting and no existing filter`() {
            // given
            val query = """from(bucket: "the-bucket") 
  |> range(start: 2018-05-22T23:30:00Z, stop: 2018-05-23T00:00:00Z)
  |> sort()"""

            // when
            val statement = InfluxDbPollStatement(query)

            // then
            assertThat(statement).all {
                prop("tieBreakerField").isEqualTo("_value")
                prop("comparatorClause").isEqualTo(">=")
                prop("rangeStartStatementRange").isNull()
                prop("filterStatementEndRange").isNull()
            }

            // when
            val timestamp = Instant.now()
            statement.saveTiebreaker(listOf(FluxRecord(1).apply {
                values["_time"] = timestamp
                values["_value"] = 5432
                values["tag"] = "this is a value"
            }))

            // then
            val expected = """from(bucket: "the-bucket") 
  |> range(start: 2018-05-22T23:30:00Z, stop: 2018-05-23T00:00:00Z)
  |> sort() |> filter(fn: (r) => (r._value >= 5432))"""
            assertThat(statement.getNextQuery().query).isEqualTo(expected)

            // when
            statement.reset()

            // then
            assertThat(statement.getNextQuery().query).isEqualTo(query)
        }

        @Test
        internal fun `should enhance the query with ascending value sorting and an existing filter`() {
            // given
            val query = """from(bucket: "the-bucket") 
  |> filter(fn: (r) => (r["_measurement"] == "temperature"))
  |> range(start: 2018-05-22T23:30:00Z, stop: 2018-05-23T00:00:00Z)
  |> sort(desc: false)"""

            // when
            val statement = InfluxDbPollStatement(query)

            // then
            assertThat(statement).all {
                prop("tieBreakerField").isEqualTo("_value")
                prop("comparatorClause").isEqualTo(">=")
                prop("rangeStartStatementRange").isNull()
                prop("filterStatementEndRange").isEqualTo(51..51)
            }

            // when
            val timestamp = Instant.now()
            statement.saveTiebreaker(listOf(FluxRecord(1).apply {
                values["_time"] = timestamp
                values["_value"] = 5432
                values["tag"] = "this is a value"
            }))

            // then
            val expected = """from(bucket: "the-bucket") 
  |> filter(fn: (r) => (r._value >= 5432 and r["_measurement"] == "temperature"))
  |> range(start: 2018-05-22T23:30:00Z, stop: 2018-05-23T00:00:00Z)
  |> sort(desc: false)"""
            assertThat(statement.getNextQuery().query).isEqualTo(expected)

            // when
            statement.reset()

            // then
            assertThat(statement.getNextQuery().query).isEqualTo(query)
        }


        @Test
        internal fun `should enhance the query with descending value sorting and no existing filter`() {
            // given
            val query = """from(bucket: "the-bucket") 
  |> range(start: 2018-05-22T23:30:00Z, stop: 2018-05-23T00:00:00Z)
  |> sort(desc: true)"""

            // when
            val statement = InfluxDbPollStatement(query)

            // then
            assertThat(statement).all {
                prop("tieBreakerField").isEqualTo("_value")
                prop("comparatorClause").isEqualTo("<=")
                prop("rangeStartStatementRange").isNull()
                prop("filterStatementEndRange").isNull()
            }

            // when
            val timestamp = Instant.now()
            statement.saveTiebreaker(listOf(FluxRecord(1).apply {
                values["_time"] = timestamp
                values["_value"] = 5432
                values["tag"] = "this is a value"
            }))

            // then
            val expected = """from(bucket: "the-bucket") 
  |> range(start: 2018-05-22T23:30:00Z, stop: 2018-05-23T00:00:00Z)
  |> sort(desc: true) |> filter(fn: (r) => (r._value <= 5432))"""
            assertThat(statement.getNextQuery().query).isEqualTo(expected)

            // when
            statement.reset()

            // then
            assertThat(statement.getNextQuery().query).isEqualTo(query)
        }

        @Test
        internal fun `should enhance the query with descending value sorting and an existing filter`() {
            // given
            val query = """from(bucket: "the-bucket") 
  |> filter(fn: (r) => (r["_measurement"] == "temperature"))
  |> range(start: 2018-05-22T23:30:00Z, stop: 2018-05-23T00:00:00Z)
  |> sort(desc: true)"""

            // when
            val statement = InfluxDbPollStatement(query)

            // then
            assertThat(statement).all {
                prop("tieBreakerField").isEqualTo("_value")
                prop("comparatorClause").isEqualTo("<=")
                prop("rangeStartStatementRange").isNull()
                prop("filterStatementEndRange").isEqualTo(51..51)
            }

            // when
            val timestamp = Instant.now()
            statement.saveTiebreaker(listOf(FluxRecord(1).apply {
                values["_time"] = timestamp
                values["_value"] = 5432
                values["tag"] = "this is a value"
            }))

            // then
            val expected = """from(bucket: "the-bucket") 
  |> filter(fn: (r) => (r._value <= 5432 and r["_measurement"] == "temperature"))
  |> range(start: 2018-05-22T23:30:00Z, stop: 2018-05-23T00:00:00Z)
  |> sort(desc: true)"""
            assertThat(statement.getNextQuery().query).isEqualTo(expected)

            // when
            statement.reset()

            // then
            assertThat(statement.getNextQuery().query).isEqualTo(query)
        }
    }

    @Nested
    inner class SortWithField {

        @Test
        internal fun `should enhance the query with default field sorting and no existing filter`() {
            // given
            val query = """from(bucket: "the-bucket") 
  |> range(start: 2018-05-22T23:30:00Z, stop: 2018-05-23T00:00:00Z)
  |> sort(columns: ["tag", "_value"])"""

            // when
            val statement = InfluxDbPollStatement(query)

            // then
            assertThat(statement).all {
                prop("tieBreakerField").isEqualTo("tag")
                prop("comparatorClause").isEqualTo(">=")
                prop("rangeStartStatementRange").isNull()
                prop("filterStatementEndRange").isNull()
            }

            // when
            val timestamp = Instant.now()
            statement.saveTiebreaker(listOf(FluxRecord(1).apply {
                values["_time"] = timestamp
                values["_value"] = 5432
                values["tag"] = "this is a value"
            }))

            // then
            val expected = """from(bucket: "the-bucket") 
  |> range(start: 2018-05-22T23:30:00Z, stop: 2018-05-23T00:00:00Z)
  |> sort(columns: ["tag", "_value"]) |> filter(fn: (r) => (r["tag"] >= "this is a value"))"""
            assertThat(statement.getNextQuery().query).isEqualTo(expected)

            // when
            statement.reset()

            // then
            assertThat(statement.getNextQuery().query).isEqualTo(query)
        }

        @Test
        internal fun `should enhance the query with default field sorting and an existing filter`() {
            // given
            val query = """from(bucket: "the-bucket") 
  |> filter(fn: (r) => (r["_measurement"] == "temperature"))
  |> range(start: 2018-05-22T23:30:00Z, stop: 2018-05-23T00:00:00Z)
  |> sort(columns: ["tag", "_value"])"""

            // when
            val statement = InfluxDbPollStatement(query)

            // then
            assertThat(statement).all {
                prop("tieBreakerField").isEqualTo("tag")
                prop("comparatorClause").isEqualTo(">=")
                prop("rangeStartStatementRange").isNull()
                prop("filterStatementEndRange").isEqualTo(51..51)
            }

            // when
            val timestamp = Instant.now()
            statement.saveTiebreaker(listOf(FluxRecord(1).apply {
                values["_time"] = timestamp
                values["_value"] = 5432
                values["tag"] = "this is a value"
            }))

            // then
            val expected = """from(bucket: "the-bucket") 
  |> filter(fn: (r) => (r["tag"] >= "this is a value" and r["_measurement"] == "temperature"))
  |> range(start: 2018-05-22T23:30:00Z, stop: 2018-05-23T00:00:00Z)
  |> sort(columns: ["tag", "_value"])"""
            assertThat(statement.getNextQuery().query).isEqualTo(expected)

            // when
            statement.reset()

            // then
            assertThat(statement.getNextQuery().query).isEqualTo(query)
        }

        @Test
        internal fun `should enhance the query with ascending field sorting and no existing filter`() {
            // given
            val query = """from(bucket: "the-bucket") 
  |> range(start: 2018-05-22T23:30:00Z, stop: 2018-05-23T00:00:00Z)
  |> sort(columns: ["tag", "_value"])"""

            // when
            val statement = InfluxDbPollStatement(query)

            // then
            assertThat(statement).all {
                prop("tieBreakerField").isEqualTo("tag")
                prop("comparatorClause").isEqualTo(">=")
                prop("rangeStartStatementRange").isNull()
                prop("filterStatementEndRange").isNull()
            }

            // when
            val timestamp = Instant.now()
            statement.saveTiebreaker(listOf(FluxRecord(1).apply {
                values["_time"] = timestamp
                values["_value"] = 5432
                values["tag"] = "this is a value"
            }))

            // then
            val expected = """from(bucket: "the-bucket") 
  |> range(start: 2018-05-22T23:30:00Z, stop: 2018-05-23T00:00:00Z)
  |> sort(columns: ["tag", "_value"]) |> filter(fn: (r) => (r["tag"] >= "this is a value"))"""
            assertThat(statement.getNextQuery().query).isEqualTo(expected)

            // when
            statement.reset()

            // then
            assertThat(statement.getNextQuery().query).isEqualTo(query)
        }

        @Test
        internal fun `should enhance the query with ascending field sorting and an existing filter`() {
            // given
            val query = """from(bucket: "the-bucket") 
  |> filter(fn: (r) => (r["_measurement"] == "temperature"))
  |> range(start: 2018-05-22T23:30:00Z, stop: 2018-05-23T00:00:00Z)
  |> sort(columns: ["tag", "_value"], desc: false)"""

            // when
            val statement = InfluxDbPollStatement(query)

            // then
            assertThat(statement).all {
                prop("tieBreakerField").isEqualTo("tag")
                prop("comparatorClause").isEqualTo(">=")
                prop("rangeStartStatementRange").isNull()
                prop("filterStatementEndRange").isEqualTo(51..51)
            }

            // when
            val timestamp = Instant.now()
            statement.saveTiebreaker(listOf(FluxRecord(1).apply {
                values["_time"] = timestamp
                values["_value"] = 5432
                values["tag"] = "this is a value"
            }))

            // then
            val expected = """from(bucket: "the-bucket") 
  |> filter(fn: (r) => (r["tag"] >= "this is a value" and r["_measurement"] == "temperature"))
  |> range(start: 2018-05-22T23:30:00Z, stop: 2018-05-23T00:00:00Z)
  |> sort(columns: ["tag", "_value"], desc: false)"""
            assertThat(statement.getNextQuery().query).isEqualTo(expected)

            // when
            statement.reset()

            // then
            assertThat(statement.getNextQuery().query).isEqualTo(query)
        }


        @Test
        internal fun `should enhance the query with descending field sorting and no existing filter`() {
            // given
            val query = """from(bucket: "the-bucket") 
  |> range(start: 2018-05-22T23:30:00Z, stop: 2018-05-23T00:00:00Z)
  |> sort(columns: ["tag", "_value"], desc: true)"""

            // when
            val statement = InfluxDbPollStatement(query)

            // then
            assertThat(statement).all {
                prop("tieBreakerField").isEqualTo("tag")
                prop("comparatorClause").isEqualTo("<=")
                prop("rangeStartStatementRange").isNull()
                prop("filterStatementEndRange").isNull()
            }

            // when
            val timestamp = Instant.now()
            statement.saveTiebreaker(listOf(FluxRecord(1).apply {
                values["_time"] = timestamp
                values["_value"] = 5432
                values["tag"] = "this is a value"
            }))

            // then
            val expected = """from(bucket: "the-bucket") 
  |> range(start: 2018-05-22T23:30:00Z, stop: 2018-05-23T00:00:00Z)
  |> sort(columns: ["tag", "_value"], desc: true) |> filter(fn: (r) => (r["tag"] <= "this is a value"))"""
            assertThat(statement.getNextQuery().query).isEqualTo(expected)

            // when
            statement.reset()

            // then
            assertThat(statement.getNextQuery().query).isEqualTo(query)
        }

        @Test
        internal fun `should enhance the query with descending field sorting and an existing filter`() {
            // given
            val query = """from(bucket: "the-bucket") 
  |> filter(fn: (r) => (r["_measurement"] == "temperature"))
  |> range(start: 2018-05-22T23:30:00Z, stop: 2018-05-23T00:00:00Z)
  |> sort(columns: ["tag", "_value"], desc: true)"""

            // when
            val statement = InfluxDbPollStatement(query)

            // then
            assertThat(statement).all {
                prop("tieBreakerField").isEqualTo("tag")
                prop("comparatorClause").isEqualTo("<=")
                prop("rangeStartStatementRange").isNull()
                prop("filterStatementEndRange").isEqualTo(51..51)
            }

            // when
            val timestamp = Instant.now()
            statement.saveTiebreaker(listOf(FluxRecord(1).apply {
                values["_time"] = timestamp
                values["_value"] = 5432
                values["tag"] = "this is a value"
            }))

            // then
            val expected = """from(bucket: "the-bucket") 
  |> filter(fn: (r) => (r["tag"] <= "this is a value" and r["_measurement"] == "temperature"))
  |> range(start: 2018-05-22T23:30:00Z, stop: 2018-05-23T00:00:00Z)
  |> sort(columns: ["tag", "_value"], desc: true)"""
            assertThat(statement.getNextQuery().query).isEqualTo(expected)

            // when
            statement.reset()

            // then
            assertThat(statement.getNextQuery().query).isEqualTo(query)
        }
    }

}
