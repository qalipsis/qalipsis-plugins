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

package io.qalipsis.plugins.influxdb.events

import assertk.assertThat
import assertk.assertions.isTrue
import com.influxdb.client.InfluxDBClientOptions
import com.influxdb.client.kotlin.InfluxDBClientKotlinFactory
import com.influxdb.query.FluxRecord
import io.aerisconsulting.catadioptre.coInvokeInvisible
import io.mockk.coJustRun
import io.mockk.every
import io.qalipsis.api.events.Event
import io.qalipsis.api.events.EventGeoPoint
import io.qalipsis.api.events.EventLevel
import io.qalipsis.api.events.EventRange
import io.qalipsis.api.events.EventTag
import io.qalipsis.api.lang.concurrentList
import io.qalipsis.api.meters.CampaignMeterRegistry
import io.qalipsis.plugins.influxdb.AbstractInfluxDbIntegrationTest
import io.qalipsis.test.mockk.relaxedMockk
import kotlinx.coroutines.delay
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.assertDoesNotThrow
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAccessor

/**
 * Complex integration test with InfluxDb containers to validate that the fields are successfully stored.
 *
 * @author Palina Bril
 */
internal class InfluxDbEventsPublisherIntegrationTest : AbstractInfluxDbIntegrationTest() {

    lateinit var configuration: InfluxDbEventsConfiguration

    // The meter registry should provide a timer that execute the expressions to record.
    private val meterRegistry: CampaignMeterRegistry = relaxedMockk {
        every { timer(any<String>(), any<String>(), any<String>(), any<Map<String, String>>()) } returns relaxedMockk {
            coJustRun { record(any<Duration>()) }
        }
    }

    @BeforeAll
    internal fun setUp() {
        configuration = InfluxDbEventsConfiguration(
        ).apply {
            batchSize = 100
            lingerPeriod = Duration.ofMinutes(10)
            publishers = 1
            username = "user"
            password = "passpasspass"
            bucket = "thebucket"
            org = "theorganization"
            url = influxDBContainer.url
        }
        client =
            InfluxDBClientKotlinFactory.create(
                InfluxDBClientOptions.builder()
                    .url(influxDBContainer.url)
                    .authenticate(
                        configuration.username,
                        configuration.password.toCharArray()
                    )
                    .org(configuration.org)
                    .build()
            )
    }

    @AfterAll
    internal fun tearDown() {
        client.close()
    }

    @Test
    @Timeout(30)
    internal fun `should export data`() = testDispatcherProvider.run {
        // given
        val publisher = InfluxDbEventsPublisher(
            this,
            this.coroutineContext,
            configuration,
            meterRegistry,
        )
        publisher.start()

        val events = mutableListOf<Event>()
        events.add(Event(name = "my-event", EventLevel.INFO))
        events.add(
            Event(
                name = "my-new-event",
                EventLevel.INFO,
                tags = listOf(
                    EventTag("key-1", "value-1"),
                    EventTag("key-2", "value-2"),
                    EventTag("key-3", " "),
                    EventTag("key-4", "")
                )
            )
        )

        val instantNow = Instant.now().minusSeconds(12)
        val zdtNow = ZonedDateTime.now(Clock.systemUTC().zone)
        val ldtNow = LocalDateTime.now().plusDays(1)
        val values = createTestData(instantNow, zdtNow, ldtNow, DateTimeFormatter.ISO_INSTANT)

        val logValues = listOf(*values.keys.toTypedArray())
        logValues.forEachIndexed { index, value ->
            events.add(Event(name = "my-event-$index", EventLevel.INFO, value = value))
        }

        // when
        publisher.coInvokeInvisible<Void>("performPublish", events)

        // then
        val result = requestEvents()
        Assertions.assertEquals(logValues.size + 2, result.stream().map { it.measurement }.distinct().count().toInt())

        // Verification of the overall values.
        result.filter { it.getValueByKey("EventLevel") != null }.forEach { hit ->
            Assertions.assertEquals("INFO", hit.getValueByKey("_value"))
        }

        // Verification of the events without values but with tags.
        assertDoesNotThrow("Item with tags should be found") {
            result.any { item ->
                kotlin.runCatching {
                    item.getValueByKey("key-1") == "value-1"
                            && item.getValueByKey("key-2") == "value-2"
                }.getOrDefault(false)
            }
        }

        // Verification of the events with values.
        logValues.forEachIndexed { index, value ->
            val searchCriteria = values.getValue(value)
            assertThat(result.any { item ->
                kotlin.runCatching {
                    item.getValueByKey("_measurement") == "my-event-$index" && searchCriteria(item)
                }.getOrDefault(false)
            }, "Item of value $value and type ${value::class} was not found").isTrue()
        }
        publisher.stop()
    }

    /**
     * Create the test data set with the value to log as key and the condition to match when asserting the data as value.
     */
    private fun createTestData(
        instantNow: Instant, zdtNow: ZonedDateTime,
        ldtNow: LocalDateTime,
        formatter: DateTimeFormatter
    ): Map<Any, (fluxRecord: FluxRecord) -> Boolean> {
        val values = mapOf<Any, ((fluxRecord: FluxRecord) -> Boolean)>(
            "my-message" to { fluxRecord -> fluxRecord.field == "message" && fluxRecord.value == "my-message" },
            true to { fluxRecord -> fluxRecord.field == "boolean" && fluxRecord.value == true },
            123.65 to { fluxRecord -> fluxRecord.field == "number" && fluxRecord.value == 123.65 },
            Double.POSITIVE_INFINITY to { fluxRecord ->
                fluxRecord.field == "number" && fluxRecord.value == "Infinity"
            },
            Double.NEGATIVE_INFINITY to { fluxRecord ->
                fluxRecord.field == "number" && fluxRecord.value == "-Infinity"
            },
            Double.NaN to { fluxRecord ->
                fluxRecord.field == "number" && fluxRecord.value == "NaN"
            },
            123.65.toFloat() to { fluxRecord -> fluxRecord.field == "number" && (fluxRecord.value as Double) in (123.65..123.66) },
            123.65.toBigDecimal() to { fluxRecord ->
                fluxRecord.field == "number" && fluxRecord.value == 123.65
            },
            123 to { fluxRecord -> fluxRecord.field == "number" && fluxRecord.value == 123L },
            123.toBigInteger() to { fluxRecord -> fluxRecord.field == "number" && fluxRecord.value == 123L },
            123.toLong() to { fluxRecord -> fluxRecord.field == "number" && fluxRecord.value == 123L },
            instantNow to { fluxRecord ->
                fluxRecord.field == "date" && formatter.parse(fluxRecord.value as String) { temporal: TemporalAccessor ->
                    Instant.from(temporal)
                } == instantNow
            },
            zdtNow to { fluxRecord ->
                fluxRecord.field == "date" && formatter.parse(fluxRecord.value as String) { temporal: TemporalAccessor ->
                    Instant.from(temporal)
                } == zdtNow.toInstant()
            },
            ldtNow to { fluxRecord ->
                fluxRecord.field == "date" && LocalDateTime.parse(fluxRecord.value as String) == ldtNow
            },
            relaxedMockk<Throwable> {
                every { message } returns "my-error"
            } to { fluxRecord ->
                fluxRecord.field == "error" && fluxRecord.value == "my-error"
            },
            Duration.ofNanos(12_123_456_789) to { fluxRecord ->
                fluxRecord.field == "duration_nanos" && fluxRecord.value == 12123456789L
            },
            EventGeoPoint(12.34, 34.76) to { fluxRecord ->
                (fluxRecord.field == "latitude" && fluxRecord.value == 12.34)
                        || (fluxRecord.field == "longitude" && fluxRecord.value == 34.76)
            },
            EventRange(12.34, 34.76, includeUpper = false) to { fluxRecord ->
                fluxRecord.field == "range" && fluxRecord.value == "[12.34 : 34.76)"
            },
            MyTestObject() to { fluxRecord ->
                fluxRecord.field == "other" && fluxRecord.value == "MyTestObject(property1=1243.65, property2=here is the test)"
            }
        )
        return values
    }

    private suspend fun requestEvents(): List<FluxRecord> {
        val result = client.getQueryKotlinApi().query(
            "from(bucket: \"${configuration.bucket}\") |> range(start: 0)"
        )
        val records = concurrentList<FluxRecord>()
        delay(3000)
        for (record in result) {
            records.add(record)
        }
        return records
    }

    data class MyTestObject(val property1: Double = 1243.65, val property2: String = "here is the test")
}
