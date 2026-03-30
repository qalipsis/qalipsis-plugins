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

package io.qalipsis.plugins.redis.lettuce.poll

import java.time.Duration

/**
 * Meters of a unique poll call using Lettuce to Redis.
 *
 * @property timeToResult time to proceed with a single poll statement
 * @property pollCount number of poll iterations perform until the cursor is finished
 * @property recordsCount number of items in the result
 * @property valuesBytesReceived number of bytes contains in all the items of the result
 *
 * @author Svetlana Paliashchuk
 */
data class LettucePollMeters(
    val timeToResult: Duration,
    val pollCount: Int,
    val recordsCount: Int,
    val valuesBytesReceived: Int,
)
