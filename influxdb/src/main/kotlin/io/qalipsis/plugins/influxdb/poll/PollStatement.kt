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

package io.qalipsis.plugins.influxdb.poll

import com.influxdb.client.domain.Query
import com.influxdb.query.FluxRecord


/**
 * statement for polling, integrating the ability to be internally modified when a tie-breaker is set.
 *
 * @author Alex Averyanov
 */
internal interface PollStatement {

    /**
     * Sort the records, which are not exactly sorted from InfluxDB in some conditions, and saves the tie-breaker for a
     * future call.
     */
    fun saveTiebreaker(records: List<FluxRecord>)

    /**
     * Changes the query following the first when the tie-breaker is already known
     * By default sort with desc = false (ascending order). If you need descending order - desc should be true.
     */
    fun getNextQuery(): Query

    /**
     * Resets the instance into the initial state to be ready for a new poll sequence starting from scratch.
     */
    fun reset()

}