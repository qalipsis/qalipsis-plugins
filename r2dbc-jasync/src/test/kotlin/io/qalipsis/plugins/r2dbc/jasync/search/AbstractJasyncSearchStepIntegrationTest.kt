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
        val tags = mapOf("kit" to "kat")
        val meterRegistry = relaxedMockk<CampaignMeterRegistry> {
            every { counter("scenario-test", "step-test", "r2dbc-jasync-search-records", refEq(tags)) } returns recordsCounter
            every { recordsCounter.report(any()) } returns recordsCounter
            every { counter("scenario-test", "step-test", "r2dbc-jasync-search-successes", refEq(tags)) } returns successCounter
            every { counter("scenario-test", "step-test", "r2dbc-jasync-search-failures", refEq(tags)) } returns successCounter
            every { successCounter.report(any()) } returns successCounter
            every { failureCounter.report(any()) } returns failureCounter
        }
        val startStopContext = relaxedMockk<StepStartStopContext> {
            every { toEventTags() } returns tags
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
