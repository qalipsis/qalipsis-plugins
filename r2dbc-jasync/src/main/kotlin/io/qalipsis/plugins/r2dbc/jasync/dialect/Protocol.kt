package io.qalipsis.plugins.r2dbc.jasync.dialect

/**
 * Protocol to apply to connect to a database.
 *
 * @author Eric Jessé
 */
enum class Protocol(internal val dialect: Dialect) {

    /**
     * For PostgreSQL.
     */
    POSTGRESQL(DialectConfigurations.POSTGRESQL),

    /**
     * For MariaDB.
     */
    MARIADB(DialectConfigurations.MYSQL),

    /**
     * For MySQL.
     */
    MYSQL(DialectConfigurations.MYSQL)
}