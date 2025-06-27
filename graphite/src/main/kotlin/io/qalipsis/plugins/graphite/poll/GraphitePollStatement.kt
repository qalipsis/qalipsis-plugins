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
