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

package io.qalipsis.plugins.graphite.poll

import io.qalipsis.plugins.graphite.poll.model.GraphiteQuery
import io.qalipsis.plugins.graphite.render.model.GraphiteMetricsRequestBuilder
import io.qalipsis.plugins.graphite.render.model.GraphiteRenderApiJsonResponse

/**
 * Graphite statement for polling, integrating the ability to be internally modified when a tie-breaker is set.
 *
 * @property tieBreaker - tie breaker instant
 * @author Sandro Mamukelashvili
 */
internal class GraphitePollStatement(
    private val graphiteQuery: GraphiteQuery
) {

    private var tieBreaker: Long? = null

    fun saveTiebreaker(records: List<GraphiteRenderApiJsonResponse>) {
        // We assume that the records are ordered chronologically.
        records.asSequence().flatMap { record -> record.dataPoints }
            .mapNotNull { it.timestamp }
            .sortedByDescending { it }
            .firstOrNull()?.let { latestTimestamp ->
                tieBreaker = latestTimestamp
            }
    }

    fun getNextQuery(): GraphiteMetricsRequestBuilder {
        return if (tieBreaker != null) {
            GraphiteMetricsRequestBuilder(graphiteQuery).from(tieBreaker!!)
        } else {
            GraphiteMetricsRequestBuilder(graphiteQuery)
        }
    }

    fun reset() {
        tieBreaker = null
    }

}
