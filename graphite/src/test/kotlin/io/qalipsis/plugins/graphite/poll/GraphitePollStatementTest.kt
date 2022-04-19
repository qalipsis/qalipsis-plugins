package io.qalipsis.plugins.graphite.poll

import assertk.assertThat
import assertk.assertions.isEqualTo
import io.qalipsis.plugins.graphite.poll.model.GraphiteQuery
import io.qalipsis.plugins.graphite.render.model.GraphiteMetricsTime
import io.qalipsis.plugins.graphite.render.model.GraphiteMetricsTimeSignUnit
import io.qalipsis.plugins.graphite.render.model.GraphiteMetricsTimeUnit
import io.qalipsis.plugins.graphite.render.model.GraphiteRenderApiJsonPairResponse
import io.qalipsis.plugins.graphite.render.model.GraphiteRenderApiJsonResponse
import org.junit.jupiter.api.Test

internal class GraphitePollStatementTest {

    @Test
    fun `should return the default query if only target is set`() {
        // given
        val graphiteQuery = GraphiteQuery("exact.key.1")

        // when
        val statement = GraphitePollStatement(graphiteQuery)

        // then
        val expectedQuery = "/render?target=${graphiteQuery.target}&format=json&noNullPoints=True"

        assertThat(statement.getNextQuery().build()).isEqualTo(expectedQuery)
    }

    @Test
    fun `should return the query if tiebreaker is set`() {
        // given
        val graphiteQuery = GraphiteQuery("exact.key.1")
        val query = "/render?target=${graphiteQuery.target}&format=json&from=123123&noNullPoints=True"

        // when
        val statement = GraphitePollStatement(graphiteQuery)
        statement.saveTiebreaker(listOf(
            GraphiteRenderApiJsonResponse(graphiteQuery.target, emptyMap(), listOf(GraphiteRenderApiJsonPairResponse(1.0, 123123L)))
        ))

        // then
        assertThat(statement.getNextQuery().build()).isEqualTo(query)
    }

    @Test
    fun `should return the default query if statement is reset`() {
        // given
        val graphiteQuery = GraphiteQuery("exact.key.1")

        // when
        val statement = GraphitePollStatement(graphiteQuery)
        statement.saveTiebreaker(listOf(
            GraphiteRenderApiJsonResponse(graphiteQuery.target, emptyMap(), listOf(GraphiteRenderApiJsonPairResponse(1.0, 123123L)))
        ))
        statement.reset()

        // then
        val expectedQuery = "/render?target=${graphiteQuery.target}&format=json&noNullPoints=True"
        assertThat(statement.getNextQuery().build()).isEqualTo(expectedQuery)
    }

    @Test
    fun `should return the right query if from is specified`() {

        //given
        val graphiteQuery = GraphiteQuery("exact.key.1")

        //when
        graphiteQuery.from(GraphiteMetricsTime(50, GraphiteMetricsTimeSignUnit.MINUS, GraphiteMetricsTimeUnit.SECONDS))
        val statement = GraphitePollStatement(graphiteQuery)

        //then
        val expectedQuery = "/render?target=${graphiteQuery.target}&format=json&from=-50s&noNullPoints=True"
        assertThat(statement.getNextQuery().build()).isEqualTo(expectedQuery)
    }

}