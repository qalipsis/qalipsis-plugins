/*
 * QALIPSIS
 * Copyright (C) 2025 AERIS IT Solutions GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package io.qalipsis.plugins.timescaledb.meter

import io.micronaut.context.annotation.ConfigurationProperties
import io.micronaut.context.annotation.Requires
import io.micronaut.core.bind.annotation.Bindable
import io.micronaut.core.util.StringUtils
import java.time.Duration
import javax.validation.constraints.Min

/**
 * Configuration for the [MeterNameStatsRefresher] in the TimescaleDB provider.
 */
@Requires(property = "meters.provider.timescaledb.enabled", value = StringUtils.TRUE, defaultValue = StringUtils.FALSE)
@ConfigurationProperties("meters.provider.timescaledb.metadata")
interface TimescaledbMeterMetadataConfiguration {

    /** How long to wait between successive refresh runs. */
    @get:Bindable(defaultValue = "PT5M")
    val refreshDelay: Duration

    /** Delay before the first refresh run after startup. */
    @get:Bindable(defaultValue = "PT1M")
    val initialDelay: Duration

    /** Minimum age of an existing stats entry before it is eligible for refresh. */
    @get:Bindable(defaultValue = "PT5M")
    val minAge: Duration

    /** Maximum number of (tenant, name) pairs refreshed per run. */
    @get:Bindable(defaultValue = "50")
    @get:Min(1)
    val chunkSize: Int
}
