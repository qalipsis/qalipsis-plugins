package io.qalipsis.plugins.r2dbc.jasync.search

import com.github.jasync.sql.db.asSuspending
import com.github.jasync.sql.db.postgresql.PostgreSQLConnectionBuilder
import org.testcontainers.containers.PostgreSQLContainerProvider
import org.testcontainers.junit.jupiter.Container

/**
 * @author Fiodar Hmyza
 */
internal class PostgresJasyncSearchStepIntegrationTest : AbstractJasyncSearchStepIntegrationTest(
    "pgsql", {
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
