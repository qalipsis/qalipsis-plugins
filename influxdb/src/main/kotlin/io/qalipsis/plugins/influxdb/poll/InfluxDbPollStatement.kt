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
