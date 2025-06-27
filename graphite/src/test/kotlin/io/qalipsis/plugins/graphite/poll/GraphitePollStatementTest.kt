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

package io.qalipsis.plugins.graphite.poll

import assertk.assertThat
import assertk.assertions.isEqualTo
import io.qalipsis.plugins.graphite.search.DataPoints
import io.qalipsis.plugins.graphite.search.GraphiteMetricsTime
import io.qalipsis.plugins.graphite.search.GraphiteMetricsTimeUnit
import io.qalipsis.plugins.graphite.search.GraphiteQuery
import org.junit.jupiter.api.Test
import java.time.Instant

internal class GraphitePollStatementTest {

    @Test
    fun `should return the default query if only target is set`() {
        // given
        val target = "exact.key.1"
        val graphiteQuery = GraphiteQuery(target)

        // when
        val statement = GraphitePollStatement(graphiteQuery)

        // then
        val expectedQuery = "?target=$target&format=json&noNullPoints=True"

        assertThat(statement.getNextQuery().build()).isEqualTo(expectedQuery)
    }

    @Test
    fun `should return the query if tiebreaker is set`() {
        // given
        val target = "exact.key.1"
        val graphiteQuery = GraphiteQuery("exact.key.1")
        val query = "?target=$target&format=json&from=123123&noNullPoints=True"

        // when
        val statement = GraphitePollStatement(graphiteQuery)
        statement.saveTiebreaker(
            listOf(
                DataPoints(
                    target,
                    emptyMap(),
                    listOf(DataPoints.DataPoint(1.0, Instant.ofEpochSecond(123123L)))
                )
            )
        )

        // then
        assertThat(statement.getNextQuery().build()).isEqualTo(query)
    }

    @Test
    fun `should return the default query if statement is reset`() {
        // given
        val target = "exact.key.1"
        val graphiteQuery = GraphiteQuery(target)

        // when
        val statement = GraphitePollStatement(graphiteQuery)
        statement.saveTiebreaker(
            listOf(
                DataPoints(
                    target,
                    emptyMap(),
                    listOf(DataPoints.DataPoint(1.0, Instant.ofEpochSecond(123123L)))
                )
            )
        )
        statement.reset()

        // then
        val expectedQuery = "?target=$target&format=json&noNullPoints=True"
        assertThat(statement.getNextQuery().build()).isEqualTo(expectedQuery)
    }

    @Test
    fun `should return the right query if from is specified`() {

        //given
        val target = "exact.key.1"
        val graphiteQuery = GraphiteQuery("exact.key.1")

        //when
        graphiteQuery.from(GraphiteMetricsTime(-50, GraphiteMetricsTimeUnit.SECONDS))
        val statement = GraphitePollStatement(graphiteQuery)

        //then
        val expectedQuery = "?target=$target&format=json&from=-50s&noNullPoints=True"
        assertThat(statement.getNextQuery().build()).isEqualTo(expectedQuery)
    }

}