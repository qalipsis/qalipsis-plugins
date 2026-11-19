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
 * Result set from a SQL query, containing a list of [SqlRow] with column name metadata.
 *
 * @author Eric Jessé
 */
class SqlResultSet(
    private val columnNames: List<String>,
    private val rows: List<SqlRow>
) : List<SqlRow> by rows {

    fun columnNames(): List<String> = columnNames
}
