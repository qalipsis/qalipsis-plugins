/*
 * QALIPSIS
 * Copyright (C) 2025 AERIS IT Solutions GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package io.qalipsis.plugins.sql.save

import io.qalipsis.plugins.sql.SqlConnection
import io.qalipsis.plugins.sql.dialect.DialectConfigurations
import org.testcontainers.containers.PostgreSQLContainerProvider
import org.testcontainers.junit.jupiter.Container

/**
 * @author Carlos Vieira
 */
internal class PostgresSqlSaveStepIntegrationTest : AbstractSqlSaveStepIntegrationTest(
    "pgsql", DialectConfigurations.POSTGRESQL, {
        DialectConfigurations.POSTGRESQL.createConnectionPool(SqlConnection(
            host = "localhost",
            port = db.firstMappedPort,
            username = db.username,
            password = db.password,
            database = db.databaseName
        ))
    }
) {

    companion object {
        @Container
        @JvmStatic
        private val db = PostgreSQLContainerProvider().newInstance()
    }
}
