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

package io.qalipsis.plugins.r2dbc.jasync.search

import assertk.all
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import com.github.jasync.sql.db.SuspendingConnection
import io.mockk.coEvery
import io.mockk.every
import io.qalipsis.api.context.StepStartStopContext
import io.qalipsis.api.events.EventsLogger
import io.qalipsis.api.meters.CampaignMeterRegistry
import io.qalipsis.api.meters.Counter
import io.qalipsis.api.meters.Meter
import io.qalipsis.plugins.r2dbc.jasync.converters.JasyncResultSetConverter
import io.qalipsis.plugins.r2dbc.jasync.converters.ResultValuesConverter
import io.qalipsis.plugins.r2dbc.jasync.poll.AbstractJasyncIntegrationTest
import io.qalipsis.test.assertk.prop
import io.qalipsis.test.io.readResource
import io.qalipsis.test.io.readResourceLines
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.coVerifyOnce
import io.qalipsis.test.mockk.relaxedMockk
import io.qalipsis.test.steps.StepTestHelper
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Duration
import java.time.LocalDateTime

/**
 * Integration test to demo the usage of the search step
 *
 * @author Fiodar Hmyza
 */
@Suppress("UNCHECKED_CAST")
@WithMockk
@Testcontainers
internal abstract class AbstractJasyncSearchStepIntegrationTest(
    private val scriptFolderBaseName: String,
    connectionPoolFactory: () -> SuspendingConnection
) : AbstractJasyncIntegrationTest(connectionPoolFactory) {

    private val resultValuesConverter: ResultValuesConverter = relaxedMockk()

    private val eventsLogger: EventsLogger = relaxedMockk()

    private val recordsCounter: Counter = relaxedMockk()

    val creationScript = readResource("schemas/$scriptFolderBaseName/create-table-buildingentries.sql").trim()

    val dropScript = readResource("schemas/$scriptFolderBaseName/drop-table-buildingentries.sql").trim()

    private val successCounter = relaxedMockk<Counter>()

    private val failureCounter = relaxedMockk<Counter>()

    @BeforeEach
    override fun setUp() {
        super.setUp()
        runBlocking {
            connection.sendQuery(creationScript)
        }
    }

    @AfterEach
    internal fun tearDown() = testDispatcherProvider.run { connection.sendQuery(dropScript) }

    @Test
    internal fun `should run the search`() = testDispatcherProvider.run {
        val query =
            "select username, timestamp from buildingentries where action = ? and enabled = ? order by timestamp"
        val parameters = listOf("IN", false)
        val metersTags = mapOf("kit" to "kat")
        val meterRegistry = relaxedMockk<CampaignMeterRegistry> {
            every {
                counter(
                    "scenario-test",
                    "step-test",
                    "r2dbc-jasync-search-records",
                    refEq(metersTags)
                )
            } returns recordsCounter
            every { recordsCounter.report(any()) } returns recordsCounter
            every {
                counter(
                    "scenario-test",
                    "step-test",
                    "r2dbc-jasync-search-successes",
                    refEq(metersTags)
                )
            } returns successCounter
            every {
                counter(
                    "scenario-test",
                    "step-test",
                    "r2dbc-jasync-search-failures",
                    refEq(metersTags)
                )
            } returns successCounter
            every { successCounter.report(any()) } returns successCounter
            every { failureCounter.report(any()) } returns failureCounter
        }
        val startStopContext = relaxedMockk<StepStartStopContext> {
            every { toMetersTags() } returns metersTags
            every { scenarioName } returns "scenario-test"
            every { stepName } returns "step-test"
        }
        val converter = JasyncResultSetBatchConverter<String>(
            resultValuesConverter,
            meterRegistry = meterRegistry,
            eventsLogger = eventsLogger
        )
        converter.start(startStopContext)
        val step = JasyncSearchStep(
            id = "step-id",
            retryPolicy = null,
            connectionsPoolFactory = { connection },
            queryFactory = { _, _ -> query },
            parametersFactory = { _, _ -> parameters },
            converter = converter as JasyncResultSetConverter<ResultSetWrapper, Any?, String>
        )
        val input = "input data"

        val context =
            StepTestHelper.createStepContext<String, JasyncSearchBatchResults<String, Map<String, Any?>>>(input)

        val expected = JasyncSearchBatchResults<Any, Any>(
            meters = JasyncSearchMeters(3, Duration.ofNanos(31215484)),
            records = listOf(
                JasyncSearchRecord(
                    ordinal = 0, value = mapOf(
                        "username" to "bob",
                        "timestamp" to LocalDateTime.of(2020, 10, 20, 12, 34, 21)
                    )
                ),
                JasyncSearchRecord(
                    ordinal = 1, value = mapOf(
                        "username" to "david",
                        "timestamp" to LocalDateTime.of(2020, 10, 20, 12, 56, 8)
                    )
                ),
                JasyncSearchRecord(
                    ordinal = 2, value = mapOf(
                        "username" to "erin",
                        "timestamp" to LocalDateTime.of(2020, 10, 20, 13, 45, 8)
                    )
                )
            ),
            input = input
        )

        val populateStatements = readResourceLines("schemas/$scriptFolderBaseName/populate-table-buildingentries.sql")
        execute(populateStatements)

        coEvery { resultValuesConverter.process(any()) } returnsArgument 0

        step.start(relaxedMockk())
        step.execute(context)

        val output = (context.output as Channel).receive().value
        assertThat(output).isInstanceOf(JasyncSearchBatchResults::class).all {
            prop("input").isNotNull().isEqualTo(expected.input)
            prop("records").isNotNull().isEqualTo(expected.records)
            prop("meters").all {
                prop("recordsCounter").isNotNull().isEqualTo(expected.meters.recordsCounter)
                prop("timeToResponse").isNotNull()
            }
        }
        coVerifyOnce {
            recordsCounter.increment(3.0)
            recordsCounter.report(any<Meter.ReportingConfiguration<Counter>.() -> Unit>())
        }
    }
}
