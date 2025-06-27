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
