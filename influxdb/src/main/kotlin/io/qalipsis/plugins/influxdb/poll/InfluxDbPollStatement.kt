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

import com.influxdb.client.domain.Query
import com.influxdb.query.FluxRecord

/**
 * InfluxDb statement for polling, integrating the ability to be internally modified when a tie-breaker is set.
 *
 * @property tieBreaker - tie breaker instant
 * @author Alex Averyanov
 */
internal class InfluxDbPollStatement(
    private val query: String
) : PollStatement {

    private val tieBreakerField: String

    private val comparatorClause: String

    private val rangeStartStatementRange: IntRange?

    private val filterStatementEndRange: IntRange?

    private val tieBreakerExtractor: ((List<FluxRecord>) -> Any?)

    private var tieBreaker: Any? = null

    init {
        val sortingMatcher = SORTING_STATEMENT_REGEX.find(query)
        if (sortingMatcher?.value.isNullOrBlank()) {
            tieBreakerField = TIME_FIELD
            comparatorClause = ">="
        } else {
            val sortingFields = SORTING_FIELDS_STATEMENT_REGEX.find(sortingMatcher!!.value)
            tieBreakerField = sortingFields?.groupValues?.first()?.trim { it == '"' } ?: VALUE_FIELD

            val sortingDirectionMatcher = SORTING_DIRECTION_STATEMENT_REGEX.find(sortingMatcher.value)
            comparatorClause = if (sortingDirectionMatcher?.groupValues?.first()?.contains("true") == true) {
                "<="
            } else {
                ">="
            }
        }

        val rangeMatcher = RANGE_STATEMENT_REGEX.find(query)
        rangeStartStatementRange = if (tieBreakerField == TIME_FIELD && !rangeMatcher?.value.isNullOrBlank()) {
            RANGE_START_STATEMENT_REGEX.find(query, rangeMatcher!!.range.first)?.range
        } else {
            null
        }

        val filterMatcher = FILTER_STATEMENT_REGEX.find(query)
        filterStatementEndRange = if (tieBreakerField != TIME_FIELD && !filterMatcher?.value.isNullOrBlank()) {
            filterMatcher!!.range.last..filterMatcher.range.last
        } else {
            null
        }

        val valueExtractor: ((FluxRecord) -> Any?) = when (tieBreakerField) {
            TIME_FIELD -> {
                { it.time }
            }
            VALUE_FIELD -> {
                { it.value }
            }
            else -> {
                { it.getValueByKey(tieBreakerField) }
            }
        }
        tieBreakerExtractor = if (comparatorClause == ">=") {
            { it.maxOfOrNull { valueExtractor(it) as Comparable<Any?> } }
        } else {
            { it.minOfOrNull { valueExtractor(it) as Comparable<Any?> } }
        }
    }

    override fun saveTiebreaker(records: List<FluxRecord>) {
        if (records.isNotEmpty()) {
            tieBreaker = tieBreakerExtractor(records)
            if (tieBreaker is String) {
                tieBreaker = "\"$tieBreaker\""
            }
        }
    }

    override fun getNextQuery(): Query {
        val modifiedQuery = if (tieBreaker != null) {
            when (tieBreakerField) {
                TIME_FIELD -> withRange()
                VALUE_FIELD -> withValueFilter()
                else -> withPropertyFilter()
            }
        } else {
            query
        }
        return Query().query(modifiedQuery)

    }

    private fun withRange(): String {
        return if (rangeStartStatementRange != null) {
            query.replaceRange(rangeStartStatementRange, "start: $tieBreaker")
        } else {
            "$query |> range(start: $tieBreaker)"
        }
    }

    private fun withValueFilter(): String {
        return if (filterStatementEndRange != null) {
            query.replaceRange(filterStatementEndRange, "(r._value $comparatorClause $tieBreaker and ")
        } else {
            "$query |> filter(fn: (r) => (r._value $comparatorClause $tieBreaker))"
        }
    }

    private fun withPropertyFilter(): String {
        return if (filterStatementEndRange != null) {
            query.replaceRange(filterStatementEndRange, """(r["$tieBreakerField"] $comparatorClause $tieBreaker and """)
        } else {
            """$query |> filter(fn: (r) => (r["$tieBreakerField"] $comparatorClause $tieBreaker))"""
        }
    }

    override fun reset() {
        tieBreaker = null
    }

    private companion object {

        /**
         * Regex to extract the filter statement of a Flux query.
         */
        val FILTER_STATEMENT_REGEX = Regex("\\|> filter([^>]*>\\s*\\()")

        /**
         * Regex to extract the sort statement of a Flux query.
         */
        val SORTING_STATEMENT_REGEX = Regex("\\|> sort\\([^)]*\\)")

        /**
         * Regex to extract the sort fields of a sort function.
         */
        val SORTING_FIELDS_STATEMENT_REGEX = Regex("\"[^\"]+\"")

        /**
         * Regex to extract the sort fields of a sort function.
         */
        val SORTING_DIRECTION_STATEMENT_REGEX = Regex("desc:\\s*(true|false)")

        /**
         * Regex to extract the range statement.
         */
        val RANGE_STATEMENT_REGEX = Regex("\\|> range\\([^)]*\\)")

        /**
         * Regex to extract the start of a range statement.
         */
        val RANGE_START_STATEMENT_REGEX = Regex("start:\\s*[^,)]+")

        /**
         * Name of the field in a record containing the time of the Point.
         */
        const val TIME_FIELD = "_time"

        /**
         * Name of the field in a record containing the value of the Point.
         */
        const val VALUE_FIELD = "_value"
    }
}
