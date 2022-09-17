/*
 * Copyright 2022 AERIS IT Solutions GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package io.qalipsis.plugins.r2dbc.jasync.dialect

import com.github.jasync.sql.db.ConnectionPoolConfiguration
import com.github.jasync.sql.db.mysql.MySQLConnectionBuilder
import com.github.jasync.sql.db.pool.ConnectionPool
import com.github.jasync.sql.db.postgresql.PostgreSQLConnectionBuilder
import org.apache.calcite.avatica.util.Quoting

/**
 * Specific configurations for the supported vendors.
 *
 * @author Eric Jessé
 */
internal object DialectConfigurations {

    /**
     * Dialect configuration for PostgreSQL.
     */
    @JvmStatic
    val POSTGRESQL = object : Dialect {
        override val quotingConfig: Quoting = Quoting.DOUBLE_QUOTE
        override val connectionBuilder: (ConnectionPoolConfiguration) -> ConnectionPool<*> =
            { PostgreSQLConnectionBuilder.createConnectionPool(it) }
    }

    /**
     * Dialect configuration for MySQL and MariaDB.
     */
    @JvmStatic
    val MYSQL = object : Dialect {
        override val quotingConfig: Quoting = Quoting.BACK_TICK
        override val connectionBuilder: (ConnectionPoolConfiguration) -> ConnectionPool<*> =
            { MySQLConnectionBuilder.createConnectionPool(it) }
    }
}
