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

package io.qalipsis.plugins.influxdb.events

import io.micronaut.context.annotation.ConfigurationProperties
import io.micronaut.context.annotation.Requires
import io.qalipsis.api.constraints.PositiveDuration
import io.qalipsis.api.events.EventLevel
import java.time.Duration
import javax.validation.constraints.Min
import javax.validation.constraints.NotNull

/**
 * Configuration for [InfluxDbEventsPublisher].
 *
 * @property minLevel minimal accepted level of events defaults to INFO.
 * @property lingerPeriod maximal period between two publication of events to InfluxDb defaults to 10 seconds.
 * @property batchSize maximal number of events buffered between two publications of events to InfluxDb defaults to 2000.
 * @property publishers number of concurrent publication of events that can be run defaults to 1 (no concurrency).
 * @property username name of the user to use for basic authentication when connecting to InfluxDb.
 * @property password password of the user to use for basic authentication when connecting to InfluxDb.
 * @property org name of the organization to use for basic authentication when connecting to InfluxDb.
 * @property bucket password of the bucket to use for saving events to InfluxDb.
 *
 */
@Requires(property = "events.export.influxdb.enabled", value = "true")
@ConfigurationProperties("events.export.influxdb")
internal class InfluxDbEventsConfiguration {

    @field:NotNull
    var url: String = "http://localhost:8086"

    @field:NotNull
    var minLevel: EventLevel = EventLevel.INFO

    @field:PositiveDuration
    var lingerPeriod: Duration = Duration.ofSeconds(10)

    @field:Min(1)
    var batchSize: Int = 40000

    @field:Min(1)
    var publishers: Int = 1

    var username: String = ""

    var password: String = ""

    var org: String = ""

    var bucket: String = ""
}
