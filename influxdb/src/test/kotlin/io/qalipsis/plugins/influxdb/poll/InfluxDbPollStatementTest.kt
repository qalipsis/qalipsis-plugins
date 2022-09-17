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
