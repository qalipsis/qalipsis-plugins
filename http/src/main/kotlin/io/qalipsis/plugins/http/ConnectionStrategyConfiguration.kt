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

package io.qalipsis.plugins.http

import io.qalipsis.api.annotations.Spec

/**
 * Configuration for the connection strategy.
 *
 * @author Francisca Eze
 */
@Spec
data class ConnectionStrategyConfiguration(
    var shared: Boolean = false,
    var strategyType: ConnectionStrategyType = ConnectionStrategyType.ON_DEMAND,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ConnectionStrategyConfiguration) return false
        return shared == other.shared && strategyType == other.strategyType
    }

    override fun hashCode(): Int {
        var result = shared.hashCode()
        result = 31 * result + strategyType.hashCode()
        return result
    }

}

/**
 * Types of connection strategies available for HTTP connections.
 *
 * ON_DEMAND: creates a new connection for each request
 * POOL: maintains a pool of connections shared among all minions
 * WARMUP: pre-initializes a pool of connections per minion
 *
 * @author Francisca Eze
 */
enum class ConnectionStrategyType {
    ON_DEMAND,
    POOL,
    WARMUP
}

@Target(AnnotationTarget.TYPE)
@DslMarker
annotation class ConnectionStrategyMarker