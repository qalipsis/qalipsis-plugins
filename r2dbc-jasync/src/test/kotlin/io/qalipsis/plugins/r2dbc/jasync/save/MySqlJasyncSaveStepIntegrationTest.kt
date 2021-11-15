package io.qalipsis.plugins.r2dbc.jasync.save

import com.github.jasync.sql.db.asSuspending
import com.github.jasync.sql.db.mysql.MySQLConnectionBuilder
import io.qalipsis.plugins.r2dbc.jasync.dialect.DialectConfigurations
import org.testcontainers.containers.MySQLContainerProvider
import org.testcontainers.junit.jupiter.Container

/**
 * @author Carlos Vieira
 */
internal class MySqlJasyncSaveStepIntegrationTest : AbstractJasyncSaveStepIntegrationTest(
    "mysql", DialectConfigurations.MYSQL, {
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
        private val db = MySQLContainerProvider().newInstance()
    }
}
