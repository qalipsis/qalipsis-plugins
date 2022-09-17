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
