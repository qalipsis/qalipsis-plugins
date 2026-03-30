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

package io.qalipsis.plugins.influxdb.save

import assertk.all
import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.index
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.key
import com.influxdb.client.domain.WritePrecision
import com.influxdb.client.write.Point
import com.influxdb.exceptions.UnprocessableEntityException
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.qalipsis.api.context.StepStartStopContext
import io.qalipsis.api.events.EventsLogger
import io.qalipsis.api.meters.CampaignMeterRegistry
import io.qalipsis.api.meters.Counter
import io.qalipsis.api.meters.Meter
import io.qalipsis.api.meters.Timer
import io.qalipsis.plugins.influxdb.AbstractInfluxDbIntegrationTest
import io.qalipsis.test.assertk.prop
import io.qalipsis.test.mockk.relaxedMockk
import kotlinx.coroutines.delay
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.assertThrows
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeUnit

internal class InfluxDbSaveStepIntegrationTest : AbstractInfluxDbIntegrationTest() {

    private val timeToResponse = relaxedMockk<Timer>()

    private val recordsCount = relaxedMockk<Counter>()

    private val successCounter = relaxedMockk<Counter>()

    private val eventsLogger = relaxedMockk<EventsLogger>()

    @Test
    @Timeout(10)
    fun `should successfully save a unique point`() = testDispatcherProvider.run {
        // given
        val tags: Map<String, String> = mockk()
        val startStopContext = relaxedMockk<StepStartStopContext> {
            every { toMetersTags() } returns tags
        }
        val meterRegistry = relaxedMockk<CampaignMeterRegistry> {
            every {
                counter(
                    startStopContext.scenarioName,
                    startStopContext.stepName,
                    "influxdb-save-saving-points",
                    refEq(tags)
                )
            } returns recordsCount
            every { recordsCount.report(any()) } returns recordsCount
            every {
                counter(
                    startStopContext.scenarioName,
                    startStopContext.stepName,
                    "influxdb-save-successes",
                    refEq(tags)
                )
            } returns successCounter
            every { successCounter.report(any()) } returns successCounter
            every {
                timer(
                    startStopContext.scenarioName,
                    startStopContext.stepName,
                    "influxdb-save-time-to-response",
                    refEq(tags)
                )
            } returns timeToResponse
        }
        val results = mutableListOf<Map<String, Any>>()
        val point = Point.measurement("temp")
            .addTag("tag1", "first")
            .addTag("tag2", "second")
            .addField("key1", "val1")
            .time(Instant.now(), WritePrecision.NS)

        val saveClient = InfluxDbSavePointClientImpl(
            clientBuilder = { client },
            meterRegistry = meterRegistry,
            eventsLogger = eventsLogger
        )

        saveClient.start(startStopContext)

        // when
        val resultOfExecute = saveClient.execute(BUCKET, ORGANIZATION, listOf(point), tags)

        // then
        assertThat(resultOfExecute).isInstanceOf(InfluxDbSaveQueryMeters::class.java).all {
            prop("savedPoints").isEqualTo(1)
        }
        val result = client.getQueryKotlinApi()
            .query("from(bucket: \"$BUCKET\") |> range(start: 0) |> filter(fn: (r) => r.tag1 == \"first\" )")

        // FIXME remove the delay
        delay(1000)
        for (record in result) {
            results.add(record.values)
        }

        assertThat(results).all {
            hasSize(1)
            index(0).all {
                key("_value").isEqualTo("val1")
            }
        }

        verify {
            eventsLogger.debug("influxdb.save.saving-points", 1, any(), tags = tags)
            timeToResponse.record(more(0L), TimeUnit.NANOSECONDS)
            recordsCount.increment(1.0)
            recordsCount.report(any<Meter.ReportingConfiguration<Counter>.() -> Unit>())
            eventsLogger.info("influxdb.save.time-to-response", any<Duration>(), any(), tags = tags)
            eventsLogger.info("influxdb.save.successes", any<Array<*>>(), any(), tags = tags)
        }
        confirmVerified(timeToResponse, recordsCount, eventsLogger)
    }

    @Test
    @Timeout(10)
    fun `should fail when sending points with date earlier than retention policy allows`(): Unit =
        testDispatcherProvider.run {
            // given
            val tags: Map<String, String> = mockk()
            val startStopContext = relaxedMockk<StepStartStopContext> {
                every { toMetersTags() } returns tags
            }
            val meterRegistry = relaxedMockk<CampaignMeterRegistry> {
                every {
                    counter(
                        startStopContext.scenarioName,
                        startStopContext.stepName,
                        "influxdb-save-saving-points",
                        refEq(tags)
                    )
                } returns recordsCount
                every { recordsCount.report(any()) } returns recordsCount
                every {
                    counter(
                        startStopContext.scenarioName,
                        startStopContext.stepName,
                        "influxdb-save-successes",
                        refEq(tags)
                    )
                } returns successCounter
                every { successCounter.report(any()) } returns successCounter
            }

            val saveClient = InfluxDbSavePointClientImpl(
                clientBuilder = { client },
                meterRegistry = meterRegistry,
                eventsLogger = eventsLogger
            )
            saveClient.start(startStopContext)

            // when + then
            assertThrows<UnprocessableEntityException> {
                saveClient.execute(
                    BUCKET,
                    ORGANIZATION,
                    listOf(
                        Point("smth").addTag("tag2", "second").addField("key2", "value2")
                            .time(Instant.now().minus(Duration.ofDays(10000)), WritePrecision.NS)
                    ), // Retention policy is just 2 days
                    tags
                )
            }
        }
}
