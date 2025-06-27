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

package io.qalipsis.plugins.influxdb.search

import io.qalipsis.api.context.StepStartStopContext
import io.qalipsis.plugins.influxdb.poll.InfluxDbQueryResult

/**
 * Client to query from InfluxDb.
 *
 * @author Palina Bril
 */
interface InfluxDbQueryClient {

    /**
     * Executes a query and returns the list of results.
     */
    suspend fun execute(query: String, contextEventTags: Map<String, String>): InfluxDbQueryResult

    /**
     * Initiate the meters if they are enabled.
     */
    suspend fun start(context: StepStartStopContext)

    /**
     * Cleans the client and closes the connections to the InfluxDB server.
     */
    suspend fun stop(context: StepStartStopContext)
}


