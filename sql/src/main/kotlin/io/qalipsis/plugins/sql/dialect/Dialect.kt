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

package io.qalipsis.plugins.sql.dialect

import io.qalipsis.plugins.sql.SqlConnection
import io.r2dbc.pool.ConnectionPool
import org.apache.calcite.avatica.util.Quoting

/**
 * Definition of database dialect to support different configurations for different vendors.
 *
 * @author Eric Jessé
 */
internal interface Dialect {

    /**
     * Configuration of the character to quote the tables or column names and avoid them
     * being interpreted as keywords.
     */
    val quotingConfig: Quoting

    /**
     * Creates a connection pool for the underlying database.
     */
    fun createConnectionPool(config: SqlConnection): ConnectionPool

    /**
     * Converts placeholder characters in SQL statements.
     * Each database has its own parameter syntax: PostgreSQL uses $1, $2;
     * MySQL/MariaDB use ?; SQL Server uses @P1, @P2; Oracle uses :1, :2.
     */
    fun convertPlaceholders(sql: String): String

    /**
     * Adds the quote characters of the dialect around [tableOrColumnName].
     * Handles bracket quoting (SQL Server) by using `[name]` instead of `[name[`.
     */
    fun quote(tableOrColumnName: String): String {
        return if (quotingConfig == Quoting.BRACKET) {
            "[$tableOrColumnName]"
        } else {
            "${quotingConfig.string}$tableOrColumnName${quotingConfig.string}"
        }
    }
}
