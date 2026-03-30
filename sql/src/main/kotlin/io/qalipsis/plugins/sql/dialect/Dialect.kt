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
