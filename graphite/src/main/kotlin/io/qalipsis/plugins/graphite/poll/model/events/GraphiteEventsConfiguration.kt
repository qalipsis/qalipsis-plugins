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

package io.qalipsis.plugins.graphite.poll.model.events

import io.micronaut.context.annotation.ConfigurationProperties
import io.micronaut.context.annotation.Requires
import io.qalipsis.api.events.EventLevel
import io.qalipsis.plugins.graphite.poll.model.events.model.GraphiteProtocol
import java.time.Duration
import javax.validation.constraints.NotBlank
import javax.validation.constraints.NotNull
import javax.validation.constraints.Positive

/**
 * ConfigurationProperties implementation for application properties
 *
 * @author rklymenko
 */
@Requires(property = "events.export.graphite.enabled", value = "true")
@ConfigurationProperties("events.export.graphite")
internal interface GraphiteEventsConfiguration {
    @get:NotNull
    val minLevel: EventLevel

    @get:NotBlank
    val host: String

    @get:Positive
    val port: Int
    val protocol: GraphiteProtocol

    @get:Positive
    val batchSize: Int

    @get:Positive
    val lingerPeriod: Duration

    @get:Positive
    val publishers: Int
}