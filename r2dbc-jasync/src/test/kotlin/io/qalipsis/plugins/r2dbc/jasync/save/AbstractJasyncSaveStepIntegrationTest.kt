/*
 * Copyright 2022 AERIS IT Solutions GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package io.qalipsis.plugins.r2dbc.jasync.save

import assertk.assertThat
import com.github.jasync.sql.db.SuspendingConnection
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.verify
import io.qalipsis.api.context.StepStartStopContext
import io.qalipsis.api.events.EventsLogger
import io.qalipsis.api.meters.CampaignMeterRegistry
import io.qalipsis.api.meters.Counter
import io.qalipsis.api.meters.Meter
import io.qalipsis.api.meters.Timer
import io.qalipsis.plugins.r2dbc.jasync.dialect.Dialect
import io.qalipsis.plugins.r2dbc.jasync.poll.AbstractJasyncIntegrationTest
import io.qalipsis.test.io.readResource
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.relaxedMockk
import io.qalipsis.test.steps.StepTestHelper
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.testcontainers.junit.jupiter.Testcontainers
import java.util.concurrent.TimeUnit

/**
 * Integration test for the usage of the save step.
 *
 * @author Carlos Vieira
 */
@WithMockk
@Testcontainers
internal abstract class AbstractJasyncSaveStepIntegrationTest(
    scriptFolderBaseName: String,
    private val dialect: Dialect,
    connectionPoolFactory: () -> SuspendingConnection
) : AbstractJasyncIntegrationTest(connectionPoolFactory) {

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
            connection.sendQuery(creationScript)
        }
    }

    @AfterEach
    internal fun tearDown(): Unit = testDispatcherProvider.run { connection.sendQuery(dropScript) }

    @Test
    @Timeout(20)
    internal fun `should run the save`() = testDispatcherProvider.run {
        val id = "step-id"
        val recordsList = listOf(JasyncSaveRecord(listOf("2020-10-20T12:34:21", "IN", "alice", true)))
        val columns = listOf("timestamp", "action", "username", "enabled")
        val tableName = "buildingentries"
        val metersTags = mapOf("kit" to "kat")
        val meterRegistry = relaxedMockk<CampaignMeterRegistry> {
            every {
                counter(
                    "scenario-test",
                    "step-test",
                    "r2dbc-jasync-save-records",
                    refEq(metersTags)
                )
            } returns recordsCounter
            every { recordsCounter.report(any()) } returns recordsCounter
            every {
                counter(
                    "scenario-test",
                    "step-test",
                    "r2dbc-jasync-save-records-success",
                    refEq(metersTags)
                )
            } returns successCounter
            every { successCounter.report(any()) } returns successCounter
            every {
                counter(
                    "scenario-test",
                    "step-test",
                    "r2dbc-jasync-save-records-failures",
                    refEq(metersTags)
                )
            } returns failureCounter
            every { failureCounter.report(any()) } returns failureCounter
            every {
                timer(
                    "scenario-test",
                    "step-test",
                    "r2dbc-jasync-save-records-time-to-response",
                    refEq(metersTags)
                )
            } returns timeToResponse
        }
        val startStopContext = relaxedMockk<StepStartStopContext> {
            every { toMetersTags() } returns metersTags
            every { scenarioName } returns "scenario-test"
            every { stepName } returns "step-test"
        }

        val step = JasyncSaveStep<String>(
            id = id,
            retryPolicy = null,
            connectionPoolBuilder = { connection },
            recordsFactory = { _, _ -> recordsList },
            columnsFactory = { _, _ -> columns },
            tableNameFactory = { _, _ -> tableName },
            dialect = dialect,
            meterRegistry = meterRegistry,
            eventsLogger = eventsLogger
        )

        val input = "input data"
        val context =
            StepTestHelper.createStepContext<String, JasyncSaveResult<String>>(input)

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

        val result = connection.sendQuery("SELECT * FROM $tableName")
        assertTrue(result.rows[0].contains("IN"))
        assertTrue(result.rows[0].contains("alice"))

        val output = (context.output as Channel).receive().value
        assertThat(output.jasyncSaveStepMeters.successSavedDocuments == 1)
        assertThat(output.input == "input data")

    }

    @Test
    @Timeout(20)
    internal fun `test save two rows success`() = testDispatcherProvider.run {
        val id = "step-id"
        val recordsList = listOf(
            JasyncSaveRecord(listOf("2020-10-20T12:34:21", "IN", "alice", true)),
            JasyncSaveRecord(listOf("2020-10-20T12:44:10", "IN", "john", false))
        )
        val columns = listOf("timestamp", "action", "username", "enabled")
        val tableName = "buildingentries"

        val metersTags = mapOf("kit" to "kat")
        val meterRegistry = relaxedMockk<CampaignMeterRegistry> {
            every {
                counter(
                    "scenario-test",
                    "step-test",
                    "r2dbc-jasync-save-records",
                    refEq(metersTags)
                )
            } returns recordsCounter
            every { recordsCounter.report(any()) } returns recordsCounter
            every {
                counter(
                    "scenario-test",
                    "step-test",
                    "r2dbc-jasync-save-records-success",
                    refEq(metersTags)
                )
            } returns successCounter
            every { successCounter.report(any()) } returns successCounter
            every {
                timer(
                    "scenario-test",
                    "step-test",
                    "r2dbc-jasync-save-records-time-to-response",
                    refEq(metersTags)
                )
            } returns timeToResponse
            every {
                counter(
                    "scenario-test",
                    "step-test",
                    "r2dbc-jasync-save-records-failures",
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

        val step = JasyncSaveStep<String>(
            id = id,
            retryPolicy = null,
            connectionPoolBuilder = { connection },
            recordsFactory = { _, _ -> recordsList },
            columnsFactory = { _, _ -> columns },
            tableNameFactory = { _, _ -> tableName },
            dialect = dialect,
            meterRegistry = meterRegistry,
            eventsLogger = eventsLogger
        )
        val input = "input data"
        val context = StepTestHelper.createStepContext<String, JasyncSaveResult<String>>(input)

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

        val result = connection.sendQuery("SELECT * FROM $tableName")
        val toCollection = result.rows.flatten()

        assertTrue(toCollection.contains("IN"))
        assertTrue(toCollection.contains("alice"))
        assertTrue(toCollection.contains("john"))

        val output = (context.output as Channel).receive().value
        assertThat(output.jasyncSaveStepMeters.successSavedDocuments == 2)
        assertThat(output.input == "input data")
    }

    @Test
    @Timeout(20)
    internal fun `should fail on save`() = testDispatcherProvider.run {
        val id = "step-id"
        val recordsList = listOf(JasyncSaveRecord(listOf("2020-10-20T12:34:21", "IN", "alice", "fail")))
        val columns = listOf("timestamp", "action", "username", "enabled")
        val tableName = "buildingentries"

        val metersTags = mapOf("kit" to "kat")
        val meterRegistry = relaxedMockk<CampaignMeterRegistry> {
            every {
                counter(
                    "scenario-test",
                    "step-test",
                    "r2dbc-jasync-save-records",
                    refEq(metersTags)
                )
            } returns recordsCounter
            every { recordsCounter.report(any()) } returns recordsCounter
            every {
                counter(
                    "scenario-test",
                    "step-test",
                    "r2dbc-jasync-save-records-success",
                    refEq(metersTags)
                )
            } returns successCounter
            every { successCounter.report(any()) } returns successCounter
            every {
                counter(
                    "scenario-test",
                    "step-test",
                    "r2dbc-jasync-save-records-failures",
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

        val step = JasyncSaveStep<String>(
            id = id,
            retryPolicy = null,
            connectionPoolBuilder = { connection },
            recordsFactory = { _, _ -> recordsList },
            columnsFactory = { _, _ -> columns },
            tableNameFactory = { _, _ -> tableName },
            dialect = dialect,
            meterRegistry = meterRegistry,
            eventsLogger = eventsLogger
        )
        val input = "input data"
        val context = StepTestHelper.createStepContext<String, JasyncSaveResult<String>>(input)
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
