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

package io.qalipsis.plugins.r2dbc.jasync.save

import com.github.jasync.sql.db.asSuspending
import com.github.jasync.sql.db.postgresql.PostgreSQLConnectionBuilder
import io.qalipsis.plugins.r2dbc.jasync.dialect.DialectConfigurations
import org.testcontainers.containers.PostgreSQLContainerProvider
import org.testcontainers.junit.jupiter.Container

/**
 * @author Carlos Vieira
 */
internal class PostgresJasyncSaveStepIntegrationTest : AbstractJasyncSaveStepIntegrationTest(
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
