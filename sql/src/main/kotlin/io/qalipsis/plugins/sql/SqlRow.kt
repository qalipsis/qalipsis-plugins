/*
 * QALIPSIS
 * Copyright (C) 2025 AERIS IT Solutions GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
