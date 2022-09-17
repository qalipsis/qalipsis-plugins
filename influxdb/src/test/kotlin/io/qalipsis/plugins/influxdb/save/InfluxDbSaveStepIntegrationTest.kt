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

package io.qalipsis.plugins.influxdb.save

import assertk.all
import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.index
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.key
import com.influxdb.client.domain.WritePrecision
import com.influxdb.client.write.Point
import com.influxdb.exceptions.UnprocessableEntityException
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tags
import io.micrometer.core.instrument.Timer
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.verify
import io.qalipsis.api.context.StepStartStopContext
import io.qalipsis.api.events.EventsLogger
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
        val metersTags = relaxedMockk<Tags>()
        val meterRegistry = relaxedMockk<MeterRegistry> {
            every { counter("influxdb-save-saving-points", refEq(metersTags)) } returns recordsCount
            every { timer("influxdb-save-time-to-response", refEq(metersTags)) } returns timeToResponse
        }
        val startStopContext = relaxedMockk<StepStartStopContext> {
            every { toMetersTags() } returns metersTags
        }
        val results = mutableListOf<Map<String, Any>>()
        val point = Point.measurement("temp")
            .addTag("tag1", "first")
            .addTag("tag2", "second")
            .addField("key1", "val1")
            .time(Instant.now().toEpochMilli() * 1000000, WritePrecision.NS)

        val saveClient = InfluxDbSavePointClientImpl(
            clientBuilder = { client },
            meterRegistry = meterRegistry,
            eventsLogger = eventsLogger
        )
        val tags: Map<String, String> = emptyMap()

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
            val metersTags = relaxedMockk<Tags>()
            val meterRegistry = relaxedMockk<MeterRegistry> {
                every { counter("influxdb-save-successes", refEq(metersTags)) } returns successCounter
            }
            val startStopContext = relaxedMockk<StepStartStopContext> {
                every { toMetersTags() } returns metersTags
            }

            val saveClient = InfluxDbSavePointClientImpl(
                clientBuilder = { client },
                meterRegistry = meterRegistry,
                eventsLogger = eventsLogger
            )
            val tags: Map<String, String> = emptyMap()
            saveClient.start(startStopContext)

            // when + then
            assertThrows<UnprocessableEntityException> {
                saveClient.execute(
                    BUCKET,
                    ORGANIZATION,
                    listOf(
                        Point("smth").addTag("tag2", "second").addField("key2", "value2")
                            .time(
                                Instant.now().minus(Duration.ofDays(10000)).toEpochMilli() * 1000000,
                                WritePrecision.NS
                            )
                    ), // Retention policy is just 2 days
                    tags
                )
            }
        }
}
