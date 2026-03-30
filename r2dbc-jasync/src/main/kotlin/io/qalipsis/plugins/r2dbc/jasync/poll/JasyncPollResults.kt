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

package io.qalipsis.plugins.r2dbc.jasync.poll

import io.qalipsis.api.steps.datasource.DatasourceRecord

/**
 * Qalispsis representation of a Jasync Poll result.
 *
 * @property records list of the Datasource records.
 * @property meters meters form the poll operation.
 *
 * @author Alex Averyanov
 */

class JasyncPollResults <O> (
    val records: List<DatasourceRecord<O>>,
    val meters: JasyncPollMeters
) : Iterable<DatasourceRecord<O>> {

    override fun iterator(): Iterator<DatasourceRecord<O>> {
        return records.iterator()
    }
}