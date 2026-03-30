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

import java.time.Duration

/**
 * Records the metrics for the Jasync poll operation.
 *
 * @property totalDocuments total number of documents
 * @property timeToResponse the time that takes on a successfully being save a records to database.
 * @property successSavedDocuments number of documents successfully save to the database.
 *
 * @author Alex Averyanov
 */
data class JasyncQueryMeters(
    val totalDocuments: Int = 0,
    val timeToResponse: Duration,
    val successSavedDocuments: Int = 0
)