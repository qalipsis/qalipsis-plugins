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

package io.qalipsis.plugins.graphite.save

import java.time.Duration

/**
 * Meters of the performed query.
 *
 * @property savedMessages count of saved messages
 * @property timeToResult time to until the confirmation of the response (successful or failed)
 *
 * @author Palina Bril
 */
data class GraphiteSaveQueryMeters(
    val savedMessages: Int,
    val timeToResult: Duration
)
