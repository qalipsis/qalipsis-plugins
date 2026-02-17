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

package io.qalipsis.plugins.sql.save

import io.qalipsis.plugins.sql.SqlConnection
import io.qalipsis.plugins.sql.dialect.DialectConfigurations
import org.testcontainers.oracle.OracleContainer
import org.testcontainers.junit.jupiter.Container

/**
 * @author Eric Jessé
 */
internal class OracleSqlSaveStepIntegrationTest : AbstractSqlSaveStepIntegrationTest(
    "oracle", DialectConfigurations.ORACLE, {
        DialectConfigurations.ORACLE.createConnectionPool(
            SqlConnection(
                host = "localhost",
                port = db.oraclePort,
                username = db.username,
                password = db.password,
                database = db.databaseName
            )
        )
    }
) {

    companion object {

        @Container
        @JvmStatic
        private val db = OracleContainer("gvenzl/oracle-free:slim-faststart")

    }
}
