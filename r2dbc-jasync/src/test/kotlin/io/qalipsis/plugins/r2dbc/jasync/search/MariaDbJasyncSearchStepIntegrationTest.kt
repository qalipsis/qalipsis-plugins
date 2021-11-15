package io.qalipsis.plugins.r2dbc.jasync.search

import com.github.jasync.sql.db.asSuspending
import com.github.jasync.sql.db.mysql.MySQLConnectionBuilder
import org.testcontainers.containers.MariaDBContainerProvider
import org.testcontainers.junit.jupiter.Container

/**
 * @author Fiodar Hmyza
 */
internal class MariaDbJasyncSearchStepIntegrationTest : AbstractJasyncSearchStepIntegrationTest(
    "mysql", {
        MySQLConnectionBuilder.createConnectionPool {
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
        private val db = MariaDBContainerProvider().newInstance()

    }
}
