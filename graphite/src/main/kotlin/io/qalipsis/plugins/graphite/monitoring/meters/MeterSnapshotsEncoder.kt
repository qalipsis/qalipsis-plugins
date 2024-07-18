/*
 * Copyright 2024 AERIS IT Solutions GmbH
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

package io.qalipsis.plugins.graphite.monitoring.meters

import io.netty.channel.ChannelHandler.Sharable
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.MessageToMessageEncoder
import io.qalipsis.api.events.Event
import io.qalipsis.api.meters.DistributionMeasurementMetric
import io.qalipsis.api.meters.MeterSnapshot
import io.qalipsis.api.meters.Statistic
import io.qalipsis.plugins.graphite.client.GraphiteRecord

/**
 * Implementation of [MessageToMessageEncoder] to convert the [Event]s into [io.qalipsis.plugins.graphite.client.GraphiteRecord]s.
 *
 * @property batchSize the number of events to map to a single message
 *
 * @author Eric Jessé.
 *
 */
@Sharable
internal class MeterSnapshotsEncoder(private val prefix: String, private val batchSize: Int = 100) :
    MessageToMessageEncoder<List<MeterSnapshot>>() {

    override fun encode(ctx: ChannelHandlerContext, msg: List<MeterSnapshot>, out: MutableList<Any>) {
        msg.windowed(batchSize, batchSize, true).forEach { snapshots ->
            out.add(
                snapshots.flatMap { snapshot ->
                    val tags = (snapshot.meterId.tags + mapOf(
                        "type" to snapshot.meterId.type.value.lowercase()
                    )).toMutableMap()

                    snapshot.measurements.map { measurement ->
                        val measurementTags = tags.toMutableMap()
                        measurementTags["measurement"] = measurement.statistic.name
                        if (measurement is DistributionMeasurementMetric && measurement.statistic == Statistic.PERCENTILE) {
                            measurementTags["percentile"] = measurement.observationPoint.toString()
                        }

                        GraphiteRecord(
                            "${prefix}${snapshot.meterId.meterName}",
                            snapshot.timestamp,
                            measurement.value,
                            tags + measurementTags
                        )
                    }
                }
            )
        }
    }

}