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

package io.qalipsis.plugins.r2dbc.jasync.save

import io.qalipsis.api.meters.Counter
import io.qalipsis.api.meters.Timer
import java.util.concurrent.TimeUnit

/**
 * Wrapper for the meters of the Jasync save operations.
 *
 * @author Carlos Vieira
 */
data class JasyncQueryMetrics(
    val recordsCount: Counter? = null,
    val timeToSuccess: Timer? = null,
    val timeToFailure: Timer? = null,
    val successCounter: Counter? = null,
    val failureCounter: Counter? = null,
) {
    /**
     * Records the number of send records.
     */
    fun countRecords(size: Int) = recordsCount?.increment(size.toDouble())

    /**
     * Records the time to response when the request is successful.
     */
    fun recordTimeToSuccess(durationNanos: Long) = timeToSuccess?.record(durationNanos, TimeUnit.NANOSECONDS)

    /**
     * Records the time to response when the request fails.
     */
    fun recordTimeToFailure(durationNanos: Long) = timeToFailure?.record(durationNanos, TimeUnit.NANOSECONDS)

    /**
     * Records a new success.
     */
    fun countSuccess() = successCounter?.increment()

    /**
     * Records a new failure.
     */
    fun countFailure() = failureCounter?.increment()
}
