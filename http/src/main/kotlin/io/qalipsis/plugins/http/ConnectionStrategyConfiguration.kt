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