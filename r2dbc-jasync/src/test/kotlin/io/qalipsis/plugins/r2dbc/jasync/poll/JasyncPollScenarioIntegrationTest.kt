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

package io.qalipsis.plugins.r2dbc.jasync.poll

import assertk.all
import assertk.assertThat
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.hasSize
import com.github.jasync.sql.db.asSuspending
import com.github.jasync.sql.db.postgresql.PostgreSQLConnectionBuilder
import io.qalipsis.plugins.r2dbc.jasync.dialect.Protocol
import io.qalipsis.runtime.test.QalipsisTestRunner
import io.qalipsis.test.io.readResource
import io.qalipsis.test.io.readResourceLines
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.testcontainers.containers.PostgreSQLContainerProvider
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

/**
 * Integration test to demo the usage of the poll operator in a scenario.
 *
 * See [PollScenario] for more details.
 *
 * @author Eric Jessé
 */
@Testcontainers
internal class JasyncPollScenarioIntegrationTest : AbstractJasyncIntegrationTest(
    {
        PostgreSQLConnectionBuilder.createConnectionPool {
            host = "localhost"
            port = db.firstMappedPort
            username = db.username
            password = db.password
            database = db.databaseName
        }.asSuspending
    }
) {

    private var scriptFolderBaseName = "pgsql"

    val creationScript = readResource("schemas/$scriptFolderBaseName/create-table-buildingentries.sql").trim()

    val dropScript =
        readResource("schemas/$scriptFolderBaseName/drop-table-buildingentries.sql").trim()

    @BeforeEach
    override fun setUp() {
        super.setUp()
        PollScenario.protocol = Protocol.POSTGRESQL
        PollScenario.dbPort = db.firstMappedPort
        PollScenario.dbUsername = db.username
        PollScenario.dbPassword = db.password
        PollScenario.dbName = db.databaseName
        runBlocking {
            connection.sendQuery(creationScript)
        }
    }

    @AfterEach
    internal fun tearDown(): Unit = testDispatcherProvider.run { connection.sendQuery(dropScript) }

    @Test
    @Timeout(30)
    internal fun `should run the poll scenario`() = testDispatcherProvider.run {
        val populateStatements = readResourceLines("schemas/$scriptFolderBaseName/populate-table-buildingentries.sql")
        execute(populateStatements)

        val exitCode = QalipsisTestRunner.withScenarios("r2dbc-poll").execute()

        Assertions.assertEquals(0, exitCode)

        assertThat(PollScenario.receivedMessages).all {
            hasSize(5)
            containsExactlyInAnyOrder(
                "The user alice stayed 48 minute(s) in the building",
                "The user bob stayed 20 minute(s) in the building",
                "The user charles stayed 1 minute(s) in the building",
                "The user david stayed 114 minute(s) in the building",
                "The user erin stayed 70 minute(s) in the building"
            )
        }
    }

    companion object {

        @Container
        @JvmStatic
        private val db = PostgreSQLContainerProvider().newInstance()

    }
}
