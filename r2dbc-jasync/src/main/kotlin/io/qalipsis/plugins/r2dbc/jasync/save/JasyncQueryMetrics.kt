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
