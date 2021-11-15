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
