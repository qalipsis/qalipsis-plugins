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
