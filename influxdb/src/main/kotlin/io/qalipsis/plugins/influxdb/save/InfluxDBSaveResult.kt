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

package io.qalipsis.plugins.influxdb.save

import com.influxdb.client.write.Point

/**
 * Wrapper for the result of save points procedure in InfluxDB.
 *
 * @property input the data to save in InfluxDB
 * @property points the data formatted to be able to save in InfluxDB
 * @property meters meters of the save step
 *
 * @author Palina Bril
 */
class InfluxDBSaveResult<I>(
    val input: I,
    val points: List<Point>,
    val meters: InfluxDbSaveQueryMeters
)
