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

package io.qalipsis.plugins.r2dbc.jasync.poll

import com.github.jasync.sql.db.RowData

/**
 * SQL statement for polling, integrating the ability to be internally modified when a tie-breaker is set.
 *
 * @author Eric Jessé
 */
internal interface SqlPollStatement {

    /**
     * Saves the boundary value used to select the next batch of data.
     */
    fun saveTiebreaker(record: RowData)

    /**
     * Provides the SQL prepared statement given the past executions and tie-breaker value.
     */
    val query: String

    /**
     * Provides the initial parameters.
     */
    val parameters: List<Any?>

    /**
     * Resets the instance into the initial state to be ready for a new poll sequence starting from scratch.
     */
    fun reset()
}