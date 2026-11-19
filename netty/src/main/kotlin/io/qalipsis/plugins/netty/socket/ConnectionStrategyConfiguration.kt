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
