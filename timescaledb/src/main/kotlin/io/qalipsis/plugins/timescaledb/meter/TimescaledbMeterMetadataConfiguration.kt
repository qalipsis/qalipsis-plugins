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
