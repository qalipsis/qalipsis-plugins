package io.qalipsis.plugins.r2dbc.jasync.dialect

import com.github.jasync.sql.db.ConnectionPoolConfiguration
import com.github.jasync.sql.db.pool.ConnectionPool
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
     * Connection builder for the underlying database.
     */
    val connectionBuilder: (ConnectionPoolConfiguration) -> ConnectionPool<*>

    /**
     * Adds the quote characters of the dialect around [tableOrColumnName].
     */
    fun quote(tableOrColumnName: String) = "${quotingConfig.string}$tableOrColumnName${quotingConfig.string}"
}
