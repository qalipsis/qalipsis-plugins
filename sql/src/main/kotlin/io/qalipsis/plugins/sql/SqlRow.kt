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

package io.qalipsis.plugins.sql

/**
 * Eagerly-materialized row from a SQL result set with column-name and index-based access.
 *
 * @author Eric Jessé
 */
class SqlRow(
    private val columnNames: List<String>,
    private val values: List<Any?>,
    private val columnIndex: Map<String, Int> = columnNames.withIndex().associate { (index, name) -> name to index }
) {

    operator fun get(column: String): Any? {
        val index = columnIndex[column] ?: columnIndex[column.lowercase()]
            ?: throw IllegalArgumentException("Column '$column' not found. Available columns: $columnNames")
        return values[index]
    }

    operator fun get(index: Int): Any? {
        return values[index]
    }
}
