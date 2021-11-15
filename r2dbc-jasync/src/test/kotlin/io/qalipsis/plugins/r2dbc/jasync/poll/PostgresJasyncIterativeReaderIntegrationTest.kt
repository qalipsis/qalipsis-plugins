package io.qalipsis.plugins.r2dbc.jasync.poll

import com.github.jasync.sql.db.asSuspending
import com.github.jasync.sql.db.postgresql.PostgreSQLConnectionBuilder
import io.qalipsis.plugins.r2dbc.jasync.dialect.DialectConfigurations
import org.testcontainers.containers.PostgreSQLContainerProvider
import org.testcontainers.junit.jupiter.Container

/**
 *
 * @author Eric Jessé
 */
internal class PostgresJasyncIterativeReaderIntegrationTest : AbstractJasyncIterativeReaderIntegrationTest(
        "pgsql", DialectConfigurations.POSTGRESQL, {
    PostgreSQLConnectionBuilder.createConnectionPool {
        host = "localhost"
        port = db.firstMappedPort
        username = db.username
        password = db.password
        database = db.databaseName
    }.asSuspending
}
) {

    companion object {

        @Container
        @JvmStatic
        private val db = PostgreSQLContainerProvider().newInstance()

    }
}