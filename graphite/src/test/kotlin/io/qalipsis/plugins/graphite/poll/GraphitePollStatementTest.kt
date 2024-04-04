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