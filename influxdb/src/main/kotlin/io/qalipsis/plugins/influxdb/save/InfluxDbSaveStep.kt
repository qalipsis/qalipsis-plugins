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

package io.qalipsis.plugins.influxdb.save

import com.influxdb.client.write.Point
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.context.StepName
import io.qalipsis.api.context.StepStartStopContext
import io.qalipsis.api.retry.RetryPolicy
import io.qalipsis.api.steps.AbstractStep

/**
 * Implementation of a [io.qalipsis.api.steps.Step] able to perform inserts into InfluxDB.
 *
 * @property influxDbSavePointClient client to use to execute the io.qalipsis.plugins.influxdb.save for the current step.
 * @property bucketName closure to generate the string for the database bucket name.
 * @property orgName closure to generate the string for the organization name.
 * @property pointsFactory closure to generate a list of [Point].
 *
 * @author Palina Bril
 */
internal class InfluxDbSaveStep<I>(
    id: StepName,
    retryPolicy: RetryPolicy?,
    private val influxDbSavePointClient: InfluxDbSavePointClient,
    private val bucketName: (suspend (ctx: StepContext<*, *>, input: I) -> String),
    private val orgName: (suspend (ctx: StepContext<*, *>, input: I) -> String),
    private val pointsFactory: (suspend (ctx: StepContext<*, *>, input: I) -> List<Point>)
) : AbstractStep<I, InfluxDBSaveResult<I>>(id, retryPolicy) {

    override suspend fun start(context: StepStartStopContext) {
        influxDbSavePointClient.start(context)
    }

    override suspend fun execute(context: StepContext<I, InfluxDBSaveResult<I>>) {
        val input = context.receive()
        val bucket = bucketName(context, input)
        val organization = orgName(context, input)
        val points = pointsFactory(context, input)

        val metrics = influxDbSavePointClient.execute(bucket, organization, points, context.toEventTags())

        context.send(InfluxDBSaveResult(input, points, metrics))
    }

    override suspend fun stop(context: StepStartStopContext) {
        influxDbSavePointClient.stop(context)
    }
}
