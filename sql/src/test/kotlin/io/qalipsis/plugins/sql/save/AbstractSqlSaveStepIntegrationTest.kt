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

import assertk.assertThat
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.verify
import io.qalipsis.api.context.StepStartStopContext
import io.qalipsis.api.events.EventsLogger
import io.qalipsis.api.meters.CampaignMeterRegistry
import io.qalipsis.api.meters.Counter
import io.qalipsis.api.meters.Meter
import io.qalipsis.api.meters.Timer
import io.qalipsis.plugins.sql.dialect.Dialect
import io.qalipsis.plugins.sql.poll.AbstractSqlIntegrationTest
import io.qalipsis.plugins.sql.r2dbc.acquireConnection
import io.qalipsis.plugins.sql.r2dbc.closeConnection
import io.qalipsis.plugins.sql.r2dbc.executePreparedQuery
import io.qalipsis.test.io.readResource
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.relaxedMockk
import io.qalipsis.test.steps.StepTestHelper
import io.r2dbc.pool.ConnectionPool
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/**
 * Integration test for the usage of the save step.
 *
 * @author Carlos Vieira
 */
@WithMockk
@Testcontainers
internal abstract class AbstractSqlSaveStepIntegrationTest(
    scriptFolderBaseName: String,
    private val dialect: Dialect,
    connectionPoolFactory: () -> ConnectionPool
) : AbstractSqlIntegrationTest(connectionPoolFactory) {

    private val eventsLogger: EventsLogger = relaxedMockk(name = "eventsLogger")

    private val recordsCounter: Counter = relaxedMockk(name = "recordsCounter")

    private val failureCounter: Counter = relaxedMockk(name = "failureCounter")

    private val successCounter: Counter = relaxedMockk(name = "successCounter")

    private val timeToResponse: Timer = relaxedMockk(name = "timeToResponse")

    private val creationScript = readResource("schemas/$scriptFolderBaseName/create-table-buildingentries.sql").trim()

    private val dropScript = readResource("schemas/$scriptFolderBaseName/drop-table-buildingentries.sql").trim()

    @BeforeEach
    override fun setUp() {
        super.setUp()
        runBlocking {
            sendQuery(creationScript)
        }
    }

    @AfterEach
    internal fun tearDown(): Unit = testDispatcherProvider.run { sendQuery(dropScript) }

    @Test
    @Timeout(20)
    internal fun `should run the save`() = testDispatcherProvider.run {
        val id = "step-id"
        val recordsList = listOf(SqlSaveRecord(listOf(LocalDateTime.of(2020, 10, 20, 12, 34, 21), "IN", "alice", true)))
        val columns = listOf("timestamp", "action", "username", "enabled")
        val tableName = "buildingentries"
        val metersTags = mapOf("kit" to "kat")
        val meterRegistry = relaxedMockk<CampaignMeterRegistry> {
            every {
                counter(
                    "scenario-test",
                    "step-test",
                    "sql-save-records",
                    refEq(metersTags)
                )
            } returns recordsCounter
            every { recordsCounter.report(any()) } returns recordsCounter
            every {
                counter(
                    "scenario-test",
                    "step-test",
                    "sql-save-records-success",
                    refEq(metersTags)
                )
            } returns successCounter
            every { successCounter.report(any()) } returns successCounter
            every {
                counter(
                    "scenario-test",
                    "step-test",
                    "sql-save-records-failures",
                    refEq(metersTags)
                )
            } returns failureCounter
            every { failureCounter.report(any()) } returns failureCounter
            every {
                timer(
                    "scenario-test",
                    "step-test",
                    "sql-save-records-time-to-response",
                    refEq(metersTags)
                )
            } returns timeToResponse
        }
        val startStopContext = relaxedMockk<StepStartStopContext> {
            every { toMetersTags() } returns metersTags
            every { scenarioName } returns "scenario-test"
            every { stepName } returns "step-test"
        }

        val step = SqlSaveStep<String>(
            id = id,
            retryPolicy = null,
            connectionPoolFactory = { connectionPool },
            recordsFactory = { _, _ -> recordsList },
            columnsFactory = { _, _ -> columns },
            tableNameFactory = { _, _ -> tableName },
            dialect = dialect,
            meterRegistry = meterRegistry,
            eventsLogger = eventsLogger
        )

        val input = "input data"
        val context =
            StepTestHelper.createStepContext<String, SqlSaveResult<String>>(input)

        step.start(startStopContext)
        step.execute(context)

        verify {
            timeToResponse.record(more(0L), TimeUnit.NANOSECONDS)
            successCounter.increment(1.0)
            recordsCounter.increment(1.0)
            recordsCounter.report(any<Meter.ReportingConfiguration<Counter>.() -> Unit>())
            successCounter.report(any<Meter.ReportingConfiguration<Counter>.() -> Unit>())
        }
        confirmVerified(timeToResponse, successCounter, recordsCounter)

        val connection = connectionPool.acquireConnection()
        try {
            val result = connection.executePreparedQuery("SELECT * FROM $tableName", emptyList())
            val firstRow = result[0]
            assertTrue(firstRow[1].toString().contains("IN") || firstRow[2].toString().contains("IN"))
            assertTrue(firstRow[2].toString().contains("alice") || firstRow[3].toString().contains("alice"))
        } finally {
            connection.closeConnection()
        }

        val output = (context.output as Channel).receive().value
        assertThat(output.sqlSaveStepMeters.successSavedDocuments == 1)
        assertThat(output.input == "input data")

    }

    @Test
    @Timeout(20)
    internal fun `test save two rows success`() = testDispatcherProvider.run {
        val id = "step-id"
        val recordsList = listOf(
            SqlSaveRecord(listOf(LocalDateTime.of(2020, 10, 20, 12, 34, 21), "IN", "alice", true)),
            SqlSaveRecord(listOf(LocalDateTime.of(2020, 10, 20, 12, 44, 10), "IN", "john", false))
        )
        val columns = listOf("timestamp", "action", "username", "enabled")
        val tableName = "buildingentries"

        val metersTags = mapOf("kit" to "kat")
        val meterRegistry = relaxedMockk<CampaignMeterRegistry> {
            every {
                counter(
                    "scenario-test",
                    "step-test",
                    "sql-save-records",
                    refEq(metersTags)
                )
            } returns recordsCounter
            every { recordsCounter.report(any()) } returns recordsCounter
            every {
                counter(
                    "scenario-test",
                    "step-test",
                    "sql-save-records-success",
                    refEq(metersTags)
                )
            } returns successCounter
            every { successCounter.report(any()) } returns successCounter
            every {
                timer(
                    "scenario-test",
                    "step-test",
                    "sql-save-records-time-to-response",
                    refEq(metersTags)
                )
            } returns timeToResponse
            every {
                counter(
                    "scenario-test",
                    "step-test",
                    "sql-save-records-failures",
                    refEq(metersTags)
                )
            } returns failureCounter
            every { failureCounter.report(any()) } returns failureCounter
        }
        val startStopContext = relaxedMockk<StepStartStopContext> {
            every { toMetersTags() } returns metersTags
            every { scenarioName } returns "scenario-test"
            every { stepName } returns "step-test"
        }

        val step = SqlSaveStep<String>(
            id = id,
            retryPolicy = null,
            connectionPoolFactory = { connectionPool },
            recordsFactory = { _, _ -> recordsList },
            columnsFactory = { _, _ -> columns },
            tableNameFactory = { _, _ -> tableName },
            dialect = dialect,
            meterRegistry = meterRegistry,
            eventsLogger = eventsLogger
        )
        val input = "input data"
        val context = StepTestHelper.createStepContext<String, SqlSaveResult<String>>(input)

        step.start(startStopContext)
        step.execute(context)

        verify {
            timeToResponse.record(more(0L), TimeUnit.NANOSECONDS)
            successCounter.increment(2.0)
            recordsCounter.report(any<Meter.ReportingConfiguration<Counter>.() -> Unit>())
            successCounter.report(any<Meter.ReportingConfiguration<Counter>.() -> Unit>())
            failureCounter.report(any<Meter.ReportingConfiguration<Counter>.() -> Unit>())
            successCounter.increment(2.0)
            recordsCounter.increment(2.0)
        }
        confirmVerified(timeToResponse, successCounter, recordsCounter)

        val connection = connectionPool.acquireConnection()
        try {
            val result = connection.executePreparedQuery("SELECT * FROM $tableName", emptyList())
            val allValues = result.flatMap { row ->
                (0 until result.columnNames().size).map { row[it]?.toString() ?: "" }
            }

            assertTrue(allValues.any { it.contains("IN") })
            assertTrue(allValues.any { it.contains("alice") })
            assertTrue(allValues.any { it.contains("john") })
        } finally {
            connection.closeConnection()
        }

        val output = (context.output as Channel).receive().value
        assertThat(output.sqlSaveStepMeters.successSavedDocuments == 2)
        assertThat(output.input == "input data")
    }

    @Test
    @Timeout(20)
    internal fun `should fail on save`() = testDispatcherProvider.run {
        val id = "step-id"
        val recordsList = listOf(SqlSaveRecord(listOf(LocalDateTime.of(2020, 10, 20, 12, 34, 21), "IN", "alice", "fail")))
        val columns = listOf("timestamp", "action", "username", "enabled")
        val tableName = "buildingentries"

        val metersTags = mapOf("kit" to "kat")
        val meterRegistry = relaxedMockk<CampaignMeterRegistry> {
            every {
                counter(
                    "scenario-test",
                    "step-test",
                    "sql-save-records",
                    refEq(metersTags)
                )
            } returns recordsCounter
            every { recordsCounter.report(any()) } returns recordsCounter
            every {
                counter(
                    "scenario-test",
                    "step-test",
                    "sql-save-records-success",
                    refEq(metersTags)
                )
            } returns successCounter
            every { successCounter.report(any()) } returns successCounter
            every {
                counter(
                    "scenario-test",
                    "step-test",
                    "sql-save-records-failures",
                    refEq(metersTags)
                )
            } returns failureCounter
            every { failureCounter.report(any()) } returns failureCounter
        }

        val startStopContext = relaxedMockk<StepStartStopContext> {
            every { toMetersTags() } returns metersTags
            every { scenarioName } returns "scenario-test"
            every { stepName } returns "step-test"
        }

        val step = SqlSaveStep<String>(
            id = id,
            retryPolicy = null,
            connectionPoolFactory = { connectionPool },
            recordsFactory = { _, _ -> recordsList },
            columnsFactory = { _, _ -> columns },
            tableNameFactory = { _, _ -> tableName },
            dialect = dialect,
            meterRegistry = meterRegistry,
            eventsLogger = eventsLogger
        )
        val input = "input data"
        val context = StepTestHelper.createStepContext<String, SqlSaveResult<String>>(input)
        step.start(startStopContext)
        step.execute(context)

        verify {
            recordsCounter.increment(1.0)
            recordsCounter.report(any<Meter.ReportingConfiguration<Counter>.() -> Unit>())
            failureCounter.increment(1.0)
            failureCounter.report(any<Meter.ReportingConfiguration<Counter>.() -> Unit>())
        }
        confirmVerified(recordsCounter, failureCounter)

        val output = (context.output as Channel).receive().value
        assertThat(output.input == "input data")
    }
}
