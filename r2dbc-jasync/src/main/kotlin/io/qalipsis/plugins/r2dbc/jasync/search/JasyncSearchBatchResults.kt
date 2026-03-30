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

package io.qalipsis.plugins.r2dbc.jasync.search

/**
 * Qalispsis representation of a Jasync Batch Search result.
 * @property input are the records that can from the previous step.
 * @property records list of the Datasource records.
 * @property meters meters form the search operation.
 *
 * @author Alex Averyanov
 */

class JasyncSearchBatchResults <I, O> (
    val input: I,
    val records: List<JasyncSearchRecord<O>>,
    val meters: JasyncSearchMeters
) : Iterable<JasyncSearchRecord<O>> {

    override fun iterator(): Iterator<JasyncSearchRecord<O>> {
        return records.iterator()
    }
}