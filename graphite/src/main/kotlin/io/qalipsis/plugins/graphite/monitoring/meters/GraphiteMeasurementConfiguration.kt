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

import io.micronaut.context.annotation.ConfigurationProperties
import io.micronaut.context.annotation.Requires
import io.qalipsis.plugins.graphite.GraphiteProtocol
import io.qalipsis.plugins.graphite.monitoring.meters.GraphiteMeasurementConfiguration.Companion.GRAPHITE_CONFIGURATION
import javax.validation.constraints.NotBlank
import javax.validation.constraints.Positive

/**
 * ConfigurationProperties implementation for application properties.
 *
 * @author Francisca Eze
 */
@Requires(property = "$GRAPHITE_CONFIGURATION.enabled", value = "true")
@ConfigurationProperties(GRAPHITE_CONFIGURATION)
internal interface GraphiteMeasurementConfiguration {

    @get:NotBlank
    val host: String

    @get:Positive
    val port: Int

    val protocol: GraphiteProtocol

    @get:Positive
    val publishers: Int

    @get:Positive
    val batchSize: Int

    @get:NotBlank
    val prefix: String

    companion object {
        const val GRAPHITE_CONFIGURATION = "meters.export.graphite"
    }
}