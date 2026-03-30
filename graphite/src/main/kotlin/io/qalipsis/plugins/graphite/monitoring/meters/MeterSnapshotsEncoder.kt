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