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
