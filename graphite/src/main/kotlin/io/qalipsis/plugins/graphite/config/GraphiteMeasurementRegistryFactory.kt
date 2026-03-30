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

package io.qalipsis.plugins.graphite.config

import io.micronaut.context.annotation.Requirements
import io.micronaut.context.annotation.Requires
import io.micronaut.core.util.StringUtils
import io.qalipsis.api.config.MetersConfig
import io.qalipsis.api.meters.MeasurementPublisherFactory
import io.qalipsis.plugins.graphite.monitoring.meters.GraphiteMeasurementConfiguration
import io.qalipsis.plugins.graphite.monitoring.meters.GraphiteMeasurementPublisher
import jakarta.inject.Singleton

/**
 * Configuration for the export of qalipsis meters to Graphite.
 *
 * @author Palina Bril
 */
@Singleton
@Requirements(
    Requires(property = MetersConfig.EXPORT_ENABLED, notEquals = StringUtils.FALSE),
    Requires(property = GraphiteMeasurementRegistryFactory.GRAPHITE_ENABLED, notEquals = StringUtils.FALSE)
)
internal class GraphiteMeasurementRegistryFactory(
    private val configuration: GraphiteMeasurementConfiguration,
) : MeasurementPublisherFactory {
    override fun getPublisher(): GraphiteMeasurementPublisher {
        return GraphiteMeasurementPublisher(configuration = configuration)
    }

    companion object {

        private const val GRAPHITE_CONFIGURATION = "${MetersConfig.EXPORT_CONFIGURATION}.graphite"

        internal const val GRAPHITE_ENABLED = "$GRAPHITE_CONFIGURATION.enabled"
    }
}
