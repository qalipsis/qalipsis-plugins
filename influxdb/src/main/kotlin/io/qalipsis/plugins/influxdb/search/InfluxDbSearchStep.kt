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

import io.qalipsis.api.context.StepContext
import io.qalipsis.api.context.StepName
import io.qalipsis.api.context.StepStartStopContext
import io.qalipsis.api.retry.RetryPolicy
import io.qalipsis.api.steps.AbstractStep


/**
 * Implementation of a [io.qalipsis.api.steps.Step] able to perform any kind of query to get records from InfluxDB.
 *
 * @property influxDbQueryClient client to use to execute the io.qalipsis.plugins.influxdb.search for the current step
 * @property queryFactory closure to generate the filter for the query
 *
 * @author Palina Bril
 */
internal class InfluxDbSearchStep<I>(
    id: StepName,
    retryPolicy: RetryPolicy?,
    private val influxDbQueryClient: InfluxDbQueryClient,
    private val queryFactory: (suspend (ctx: StepContext<*, *>, input: I) -> String),
) : AbstractStep<I, InfluxDbSearchResult<I>>(id, retryPolicy) {

    override suspend fun start(context: StepStartStopContext) {
        influxDbQueryClient.start(context)
    }

    override suspend fun execute(context: StepContext<I, InfluxDbSearchResult<I>>) {
        val input = context.receive()
        val query = queryFactory(context, input)
        val results = influxDbQueryClient.execute(query, context.toEventTags())
        context.send(InfluxDbSearchResult(input, results.results, results.meters))
    }

    override suspend fun stop(context: StepStartStopContext) {
        influxDbQueryClient.stop(context)
    }
}
