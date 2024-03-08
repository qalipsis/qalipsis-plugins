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

package io.qalipsis.plugins.graphite.poll.model

import io.qalipsis.plugins.graphite.render.model.GraphiteMetricsTime
import io.qalipsis.plugins.graphite.render.model.GraphiteMetricsTimeUnit
import io.qalipsis.plugins.graphite.render.model.GraphiteRenderAggregationFuncName

/**
 * Wrapper class to describe structure of a graphite query
 */
data class GraphiteQuery(val target: String) {

    var from = ""

    var noNullPoints = true

    var aggregateFunction = GraphiteRenderAggregationFuncName.NONE

    /**
     * method to specify the time point from which to poll data
     *
     * @param from which specifies the particular time point from which to search data
     */
    fun from(from: Long): GraphiteQuery {
        this.from = from.toString()
        return this
    }

    /**
     * method to specify the time point from which to poll data
     *
     * @param from which specifies the particular time point from which to search data, which should be negative for values in the past
     * @param unit unit
     */
    fun from(from: Long, unit: GraphiteMetricsTimeUnit): GraphiteQuery {
        this.from = GraphiteMetricsTime(from, unit).toString()
        return this
    }

    /**
     * method to specify the time point from which to poll data
     *
     * @param from which specifies the particular time point from which to search data, as expected by the Graphite server
     */
    fun from(from: String): GraphiteQuery {
        this.from = from
        return this
    }

    /**
     * method to specify the time from which to poll data
     *
     * @param from which specifies the particular time point from which to search data
     */
    fun from(from: GraphiteMetricsTime): GraphiteQuery {
        this.from = from.toQueryString()
        return this
    }

    /**
     * toggles null points
     *
     * @param disableNullPoints a boolean to specify if null points should be enabled or disabled
     */
    fun noNullPoints(disableNullPoints: Boolean): GraphiteQuery {
        this.noNullPoints = disableNullPoints
        return this
    }

    /**
     * intermediate method called on query to perform grouping based on the params entered
     *
     * @param aggregateFunction which is an enum of the type of method to be called
     */
    fun aggregateFunction(aggregateFunction: GraphiteRenderAggregationFuncName): GraphiteQuery {
        this.aggregateFunction = aggregateFunction
        return this
    }
}