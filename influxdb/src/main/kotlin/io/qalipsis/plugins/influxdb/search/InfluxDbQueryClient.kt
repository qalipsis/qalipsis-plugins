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


