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

package io.qalipsis.plugins.graphite.poll

import assertk.assertThat
import assertk.assertions.isEqualTo
import io.qalipsis.plugins.graphite.search.DataPoints
import io.qalipsis.plugins.graphite.search.GraphiteMetricsTime
import io.qalipsis.plugins.graphite.search.GraphiteMetricsTimeUnit
import io.qalipsis.plugins.graphite.search.GraphiteQuery
import java.time.Instant
import org.junit.jupiter.api.Test

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