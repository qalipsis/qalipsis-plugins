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
import io.r2dbc.pool.ConnectionPoolConfiguration
import io.r2dbc.spi.ConnectionFactories
import io.r2dbc.spi.ConnectionFactoryOptions
import org.apache.calcite.avatica.util.Quoting

/**
 * Specific configurations for the supported vendors.
 *
 * @author Eric Jessé
 */
internal object DialectConfigurations {

    private fun buildConnectionPool(driver: String, config: SqlConnection): ConnectionPool {
        val options = ConnectionFactoryOptions.builder()
            .option(ConnectionFactoryOptions.DRIVER, driver)
            .option(ConnectionFactoryOptions.HOST, config.host)
            .option(ConnectionFactoryOptions.PORT, config.port)
            .apply {
                config.database?.let { option(ConnectionFactoryOptions.DATABASE, it) }
                config.username?.let { option(ConnectionFactoryOptions.USER, it) }
                config.password?.let { option(ConnectionFactoryOptions.PASSWORD, it) }
            }
            .build()

        val connectionFactory = ConnectionFactories.get(options)
        val poolConfig = ConnectionPoolConfiguration.builder(connectionFactory)
            .maxSize(config.maxSize)
            .maxIdleTime(config.maxIdleTime)
            .maxCreateConnectionTime(config.maxCreateConnectionTime)
            .build()

        return ConnectionPool(poolConfig)
    }

    /**
     * Dialect configuration for PostgreSQL.
     */
    @JvmStatic
    val POSTGRESQL = object : Dialect {
        override val quotingConfig: Quoting = Quoting.DOUBLE_QUOTE

        override fun createConnectionPool(config: SqlConnection): ConnectionPool =
            buildConnectionPool("postgresql", config)

        override fun convertPlaceholders(sql: String): String {
            val result = StringBuilder()
            var paramIndex = 1
            var i = 0
            while (i < sql.length) {
                val c = sql[i]
                if (c == '\'') {
                    // Skip string literals
                    result.append(c)
                    i++
                    while (i < sql.length && sql[i] != '\'') {
                        result.append(sql[i])
                        i++
                    }
                    if (i < sql.length) {
                        result.append(sql[i])
                    }
                } else if (c == '?') {
                    result.append('$').append(paramIndex++)
                } else {
                    result.append(c)
                }
                i++
            }
            return result.toString()
        }
    }

    /**
     * Dialect configuration for MySQL.
     */
    @JvmStatic
    val MYSQL = object : Dialect {
        override val quotingConfig: Quoting = Quoting.BACK_TICK

        override fun createConnectionPool(config: SqlConnection): ConnectionPool =
            buildConnectionPool("mysql", config)

        override fun convertPlaceholders(sql: String): String = sql
    }

    /**
     * Dialect configuration for MariaDB.
     */
    @JvmStatic
    val MARIADB = object : Dialect {
        override val quotingConfig: Quoting = Quoting.BACK_TICK

        override fun createConnectionPool(config: SqlConnection): ConnectionPool =
            buildConnectionPool("mariadb", config)

        override fun convertPlaceholders(sql: String): String = sql
    }
}
