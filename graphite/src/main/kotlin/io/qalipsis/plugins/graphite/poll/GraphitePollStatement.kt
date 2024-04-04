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

import io.qalipsis.plugins.graphite.search.DataPoints
import io.qalipsis.plugins.graphite.search.GraphiteQuery
import java.time.Instant

/**
 * Graphite statement for polling, integrating the ability to be internally modified when a tie-breaker is set.
 *
 * @property tieBreaker - tie breaker instant
 * @author Sandro Mamukelashvili
 */
internal class GraphitePollStatement(
    private val graphiteQuery: GraphiteQuery
) {

    private var tieBreaker: Instant? = null

    fun saveTiebreaker(records: List<DataPoints>) {
        records.asSequence().flatMap { record -> record.dataPoints }.maxOfOrNull { it.timestamp }
            ?.let { latestTimestamp -> tieBreaker = latestTimestamp }
    }

    fun getNextQuery(): GraphiteQuery {
        return tieBreaker?.let { graphiteQuery.copy().from(it) }
            ?: graphiteQuery
    }

    fun reset() {
        tieBreaker = null
    }

}
