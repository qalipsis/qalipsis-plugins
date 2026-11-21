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
import io.r2dbc.pool.ConnectionPoolConfiguration
import io.r2dbc.spi.Connection
import io.r2dbc.spi.ConnectionFactories
import io.r2dbc.spi.ConnectionFactoryOptions
import io.r2dbc.spi.R2dbcNonTransientResourceException
import io.r2dbc.spi.ValidationDepth
import org.apache.calcite.avatica.util.Quoting
import reactor.core.publisher.Mono

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
            // A failure before the release makes the pool destroy the connection instead of reusing it.
            .preRelease(::rejectClosedConnection)
            .build()

        return ConnectionPool(poolConfig)
    }

    private fun rejectClosedConnection(connection: Connection): Mono<Void> =
        Mono.from(connection.validate(ValidationDepth.LOCAL))
            .filter { valid -> !valid }
            .flatMap { Mono.error<Void>(R2dbcNonTransientResourceException("The connection is closed")) }

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

    /**
     * Dialect configuration for Microsoft SQL Server.
     */
    @JvmStatic
    val MSSQL = object : Dialect {
        override val quotingConfig: Quoting = Quoting.BRACKET

        override fun createConnectionPool(config: SqlConnection): ConnectionPool =
            buildConnectionPool("mssql", config)

        override fun convertPlaceholders(sql: String): String {
            val result = StringBuilder()
            var paramIndex = 1
            var i = 0
            while (i < sql.length) {
                val c = sql[i]
                if (c == '\'') {
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
                    result.append("@P").append(paramIndex++)
                } else {
                    result.append(c)
                }
                i++
            }
            return result.toString()
        }
    }

    /**
     * Dialect configuration for Oracle Database.
     */
    @JvmStatic
    val ORACLE = object : Dialect {
        override val quotingConfig: Quoting = Quoting.DOUBLE_QUOTE

        override fun createConnectionPool(config: SqlConnection): ConnectionPool =
            buildConnectionPool("oracle", config)

        override fun quote(tableOrColumnName: String): String =
            "\"${tableOrColumnName.uppercase()}\""

        override fun convertPlaceholders(sql: String): String {
            val result = StringBuilder()
            var paramIndex = 1
            var i = 0
            while (i < sql.length) {
                val c = sql[i]
                if (c == '\'') {
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
                    result.append(':').append(paramIndex++)
                } else {
                    result.append(c)
                }
                i++
            }
            return result.toString()
        }
    }
}
