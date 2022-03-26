package io.qalipsis.plugins.graphite.events

import io.micronaut.context.annotation.ConfigurationProperties
import io.micronaut.context.annotation.Requires
import io.qalipsis.api.constraints.PositiveDuration
import io.qalipsis.api.events.EventLevel
import io.qalipsis.plugins.graphite.events.model.GraphiteProtocol
import java.time.Duration
import javax.validation.constraints.Min
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