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

package io.qalipsis.plugins.influxdb.config

import io.micronaut.context.annotation.Requirements
import io.micronaut.context.annotation.Requires
import io.micronaut.core.util.StringUtils
import io.qalipsis.api.Executors
import io.qalipsis.api.config.MetersConfig
import io.qalipsis.api.meters.MeasurementPublisherFactory
import io.qalipsis.plugins.influxdb.meters.InfluxDbMeasurementConfiguration
import io.qalipsis.plugins.influxdb.meters.InfluxdbMeasurementPublisher
import jakarta.inject.Named
import jakarta.inject.Singleton
import kotlinx.coroutines.CoroutineScope

/**
 * Factory class responsible for supplying InfluxDbMeasurementPublisher bean with its necessary configurations.
 *
 * @author Francisca Eze
 */
@Singleton
@Requirements(
    Requires(property = MetersConfig.EXPORT_ENABLED, value = StringUtils.TRUE),
    Requires(property = InfluxDbMeasurementConfiguration.INFLUXDB_ENABLED, value = StringUtils.TRUE)
)
internal class InfluxDbMeasurementPublisherFactory(
    @Named(Executors.BACKGROUND_EXECUTOR_NAME) private val coroutineScope: CoroutineScope,
    private val configuration: InfluxDbMeasurementConfiguration
) : MeasurementPublisherFactory {

    override fun getPublisher(): InfluxdbMeasurementPublisher {
        return InfluxdbMeasurementPublisher(
            configuration = configuration,
            coroutineScope = coroutineScope
        )
    }
}
