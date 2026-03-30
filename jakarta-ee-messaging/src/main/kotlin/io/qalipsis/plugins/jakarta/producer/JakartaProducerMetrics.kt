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

package io.qalipsis.plugins.jakarta.producer

import io.qalipsis.api.meters.Counter

/**
 * Wrapper for the meters of the Jakarta producer operations.
 *
 * @author Krawist Ngoben
 */
internal data class JakartaProducerMetrics(
    private val producedBytesCounter: Counter? = null,
    private val producedRecordsCounter: Counter? = null,
) {
    /**
     * Records the number of sent bytes.
     */
    fun countBytes(size: Double) = producedBytesCounter?.increment(size)

    /**
     * Records the number of sent records.
     */
    fun countRecord() = producedRecordsCounter?.increment()

}
