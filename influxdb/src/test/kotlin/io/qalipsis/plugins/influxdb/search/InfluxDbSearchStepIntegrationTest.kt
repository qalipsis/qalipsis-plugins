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

package io.qalipsis.plugins.influxdb.search

import assertk.all
import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.index
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.prop
import com.influxdb.client.domain.WritePrecision
import com.influxdb.client.kotlin.InfluxDBClientKotlin
import com.influxdb.client.write.Point
import com.influxdb.exceptions.NotFoundException
import com.influxdb.query.FluxRecord
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.mockk
import io.mockk.verify
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.context.StepStartStopContext
import io.qalipsis.api.events.EventsLogger
import io.qalipsis.api.meters.CampaignMeterRegistry
import io.qalipsis.api.meters.Counter
import io.qalipsis.api.meters.Meter
import io.qalipsis.api.meters.Timer
import io.qalipsis.plugins.influxdb.AbstractInfluxDbIntegrationTest
import io.qalipsis.plugins.influxdb.InfluxDbStepConnectionImpl
import io.qalipsis.plugins.influxdb.poll.InfluxDbQueryMeters
import io.qalipsis.test.assertk.prop
import io.qalipsis.test.io.readResourceLines
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.relaxedMockk
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit

/**
 *
 * @author Palina Bril
 */
@WithMockk
internal class InfluxDbSearchStepIntegrationTest : AbstractInfluxDbIntegrationTest() {

    private val eventsLogger = relaxedMockk<EventsLogger>()

    private val timeToResponse = relaxedMockk<Timer>()

    private val recordsCount = relaxedMockk<Counter>()

    private val successCounter = relaxedMockk<Counter>()

    private val failureCounter = relaxedMockk<Counter>()

    @RelaxedMockK
    private lateinit var context: StepContext<Any, Pair<Any, List<FluxRecord>>>

    @Test
    @Timeout(50)
    fun `should succeed when sending query`() = testDispatcherProvider.run {
        // given
        populateInfluxFromCsv("input/all_documents.csv")

        val clientFactory: () -> InfluxDBClientKotlin = relaxedMockk()
        every { clientFactory.invoke() } returns client

        val metersTags: Map<String, String> = mockk()
        val eventsTags: Map<String, String> = mockk()
        val meterRegistry = relaxedMockk<CampaignMeterRegistry> {
            every {
                counter(
                    context.scenarioName,
                    context.stepName,
                    "influxdb-search-received-records",
                    refEq(metersTags)
                )
            } returns recordsCount
            every { recordsCount.report(any()) } returns recordsCount
            every {
                counter(
                    context.scenarioName,
                    context.stepName,
                    "influxdb-search-success",
                    refEq(metersTags)
                )
            } returns successCounter
            every { successCounter.report(any()) } returns successCounter
            every {
                timer(
                    context.scenarioName,
                    context.stepName,
                    "influxdb-search-time-to-response",
                    refEq(metersTags)
                )
            } returns timeToResponse
            every { timeToResponse.report { any() } } returns timeToResponse
            every {
                counter(
                    context.scenarioName,
                    context.stepName,
                    "influxdb-search-failure",
                    refEq(metersTags)
                )
            } returns failureCounter
            every { failureCounter.report(any()) } returns failureCounter
        }
        val startStopContext = relaxedMockk<StepStartStopContext> {
            every { toMetersTags() } returns metersTags
        }
        val config = InfluxDbStepConnectionImpl()
        config.basic("user", "passpasspass")
        config.server(influxDBContainer.url, BUCKET, ORGANIZATION)

        val searchClient = InfluxDbQueryClientImpl(
            clientFactory = clientFactory,
            meterRegistry = meterRegistry,
            eventsLogger = eventsLogger
        )
        searchClient.start(startStopContext)

        // when
        val query =
            "from(bucket: \"$BUCKET\") |> range(start: ${YESTERDAY}T12:36:09Z) |> filter(fn: (r) => (r._value == \"Driving\"))"
        val results = searchClient.execute(query, eventsTags)

        // then
        assertThat(results.results).all {
            hasSize(3)
            index(0).all {
                prop(FluxRecord::getMeasurement).isEqualTo("Car #1")
                prop(FluxRecord::getValue).isEqualTo("Driving")
                prop(FluxRecord::getTime).isEqualTo(Instant.parse("${YESTERDAY}T12:47:16Z"))
            }
            index(1).all {
                prop(FluxRecord::getMeasurement).isEqualTo("Car #2")
                prop(FluxRecord::getValue).isEqualTo("Driving")
                prop(FluxRecord::getTime).isEqualTo(Instant.parse("${YESTERDAY}T12:47:16Z"))
            }
            index(2).all {
                prop(FluxRecord::getMeasurement).isEqualTo("Truck #1")
                prop(FluxRecord::getValue).isEqualTo("Driving")
                prop(FluxRecord::getTime).isEqualTo(Instant.parse("${YESTERDAY}T12:47:16Z"))
            }
        }
        assertThat(results.meters).isInstanceOf(InfluxDbQueryMeters::class.java).all {
            prop("fetchedRecords").isEqualTo(3)
            prop("timeToResult").isNotNull()
        }
        verify {
            timeToResponse.record(more(0L), TimeUnit.NANOSECONDS)
            recordsCount.increment(3.0)
            recordsCount.report(any<Meter.ReportingConfiguration<Counter>.() -> Unit>())
            eventsLogger.debug("influxdb.search.searching", any(), any(), tags = refEq(eventsTags))
            eventsLogger.info("influxdb.search.success", any(), any(), tags = refEq(eventsTags))
        }
        confirmVerified(timeToResponse, recordsCount, eventsLogger)
    }

    @Test
    @Timeout(10)
    fun `should fail because of the wrong bucket name`() = testDispatcherProvider.run {
        // given
        populateInfluxFromCsv("input/all_documents.csv")

        val clientFactory: () -> InfluxDBClientKotlin = relaxedMockk()
        every { clientFactory.invoke() } returns client

        val tags: Map<String, String> = mockk()

        val meterRegistry = relaxedMockk<CampaignMeterRegistry> {
            every {
                counter(
                    context.scenarioName,
                    context.stepName,
                    "influxdb-search-received-records",
                    refEq(tags)
                )
            } returns recordsCount
            every { recordsCount.report(any()) } returns recordsCount
            every {
                counter(
                    context.scenarioName,
                    context.stepName,
                    "influxdb-search-success",
                    refEq(tags)
                )
            } returns successCounter
            every { successCounter.report(any()) } returns successCounter
            every {
                timer(
                    context.scenarioName,
                    context.stepName,
                    "influxdb-search-time-to-response",
                    refEq(tags)
                )
            } returns timeToResponse
            every {
                counter(
                    context.scenarioName,
                    context.stepName,
                    "influxdb-search-failure",
                    refEq(tags)
                )
            } returns failureCounter
            every { failureCounter.report(any()) } returns failureCounter
        }
        val startStopContext = relaxedMockk<StepStartStopContext> {
            every { toMetersTags() } returns tags
        }
        val config = InfluxDbStepConnectionImpl()
        config.basic("user", "passpasspass")
        config.server(influxDBContainer.url, BUCKET, ORGANIZATION)

        //bucket with such name doesn't exist
        val timestamp = Instant.now().truncatedTo(ChronoUnit.SECONDS)
        val query =
            "from(bucket: \"NOTEXIST\") |> range(start: $timestamp) |> filter(fn: (r) => (r._value == \"Driving\")) "

        val searchClient = InfluxDbQueryClientImpl(
            clientFactory = clientFactory,
            meterRegistry = meterRegistry,
            eventsLogger = eventsLogger
        )

        searchClient.start(startStopContext)

        // when + then
        assertThrows<NotFoundException> {
            searchClient.execute(
                query,
                context.toEventTags()
            )
        }
    }

    private suspend fun populateInfluxFromCsv(name: String) {
        val points = dbRecordsFromCsv(name)
        client.getWriteKotlinApi().writePoints(points)
    }

    private fun dbRecordsFromCsv(name: String): List<Point> {
        var i = 0
        return this.readResourceLines(name).map {
            i++
            val values = it.replace("{date}", YESTERDAY).split(";")
            val timestamp = Instant.parse(values[0])
            Point.measurement(values[1])
                .addTag("tag$i", "" + i)
                .addField("action", values[2])
                .time(timestamp, WritePrecision.NS)
        }
    }

    private companion object {

        val YESTERDAY = "${LocalDate.now().minusDays(1)}"

    }

}
