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

package io.qalipsis.plugins.influxdb.poll

import com.influxdb.query.FluxRecord

/**
 * Wrapper for the result of poll in InfluxDb.
 *
 * @property results list of InfluxDb records.
 * @property meters of the poll step.
 *
 * @author Alex Averyanov
 */
class InfluxDbPollResults(
    val results: List<FluxRecord>,
    val meters: InfluxDbQueryMeters
) : Iterable<FluxRecord> {

    override fun iterator(): Iterator<FluxRecord> {
        return results.iterator()
    }
}
