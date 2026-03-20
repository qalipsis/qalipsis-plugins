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

package io.qalipsis.plugins.netty.socket

import io.qalipsis.api.annotations.Spec

/**
 * Configuration for the connection strategy.
 *
 * @author Eric Jesse
 */
@Spec
data class ConnectionStrategyConfiguration(
    var shared: Boolean = false,
    var strategyType: ConnectionStrategyType = ConnectionStrategyType.ON_DEMAND,
)

/**
 * Types of connection strategies available for socket connections.
 *
 * ON_DEMAND: creates a new connection for each minion (default, equivalent to Simple*Step)
 * POOL: maintains a pool of connections shared among all minions (equivalent to Pooled*Step)
 * WARMUP: pre-initializes connections for all minions at step start
 *
 * @author Eric Jesse
 */
enum class ConnectionStrategyType {
    ON_DEMAND,
    POOL,
    WARMUP
}

@Target(AnnotationTarget.TYPE)
@DslMarker
annotation class ConnectionStrategyMarker
