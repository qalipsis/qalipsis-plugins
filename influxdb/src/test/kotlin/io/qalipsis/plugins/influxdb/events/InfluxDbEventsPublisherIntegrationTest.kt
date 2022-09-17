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

package io.qalipsis.plugins.influxdb.events

import com.influxdb.client.InfluxDBClientOptions
import com.influxdb.client.kotlin.InfluxDBClientKotlinFactory
import com.influxdb.query.FluxRecord
import io.aerisconsulting.catadioptre.coInvokeInvisible
import io.micrometer.core.instrument.MeterRegistry
import io.mockk.every
import io.qalipsis.api.events.Event
import io.qalipsis.api.events.EventGeoPoint
import io.qalipsis.api.events.EventLevel
import io.qalipsis.api.events.EventRange
import io.qalipsis.api.events.EventTag
import io.qalipsis.api.lang.concurrentList
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
import java.time.ZoneId
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
    private val meterRegistry: MeterRegistry = relaxedMockk {
        every { timer(any(), *anyVararg()) } returns relaxedMockk {
            every { record(any<Runnable>()) } answers { (firstArg() as Runnable).run() }
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
                tags = listOf(EventTag("key-1", "value-1"), EventTag("key-2", "value-2"))
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
        Assertions.assertEquals(logValues.size + 2, result.stream().map { it.table }.distinct().count().toInt())

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
            assertDoesNotThrow("Item of value $value and type ${value::class} was not found") {
                result.any { item ->
                    kotlin.runCatching {
                        "my-event-$index" == item.getValueByKey("_measurement") && searchCriteria(item)
                    }.getOrDefault(false)
                }
            }
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
            "my-message" to { fluxRecord -> fluxRecord.getValueByKey("message") == "my-message" },
            true to { fluxRecord -> fluxRecord.getValueByKey("boolean").toString().toBoolean() },
            123.65 to { fluxRecord -> fluxRecord.getValueByKey("number").toString().toDouble() == 123.65 },
            Double.POSITIVE_INFINITY to { fluxRecord ->
                fluxRecord.getValueByKey("number") == "Infinity"
            },
            Double.NEGATIVE_INFINITY to { fluxRecord ->
                fluxRecord.getValueByKey("number") == "-Infinity"
            },
            Double.NaN to { fluxRecord ->
                fluxRecord.getValueByKey("number") == "NaN"
            },
            123.65.toFloat() to { fluxRecord -> fluxRecord.getValueByKey("number").toString().toDouble() == 123.65 },
            123.65.toBigDecimal() to { fluxRecord ->
                fluxRecord.getValueByKey("number").toString().toDouble() == 123.65
            },
            123 to { fluxRecord -> fluxRecord.getValueByKey("number").toString().toInt() == 123 },
            123.toBigInteger() to { fluxRecord -> fluxRecord.getValueByKey("number").toString().toInt() == 123 },
            123.toLong() to { fluxRecord -> fluxRecord.getValueByKey("number").toString().toInt() == 123 },
            instantNow to { fluxRecord ->
                formatter.parse(
                    fluxRecord.getValueByKey("date").toString()
                ) { temporal: TemporalAccessor ->
                    Instant.from(temporal)
                } == instantNow
            },
            zdtNow to { fluxRecord ->
                formatter.parse(
                    fluxRecord.getValueByKey("date").toString()
                ) { temporal: TemporalAccessor ->
                    Instant.from(temporal)
                } == zdtNow.toInstant()
            },
            ldtNow to { fluxRecord ->
                formatter.parse(
                    fluxRecord.getValueByKey("date").toString() + "Z"
                ) { temporal: TemporalAccessor ->
                    Instant.from(temporal)
                } == ldtNow.atZone(ZoneId.systemDefault()).toInstant()
            },
            relaxedMockk<Throwable> {
                every { message } returns "my-error"
            } to { fluxRecord ->
                fluxRecord.getValueByKey("error") == "my-error"
            },
            Duration.ofNanos(12_123_456_789) to { fluxRecord ->
                fluxRecord.getValueByKey("duration") == "PT12.123456789S"
            },
            EventGeoPoint(12.34, 34.76) to { fluxRecord ->
                fluxRecord.getValueByKey("latitude").toString().toDouble() == 12.34
                fluxRecord.getValueByKey("longitude").toString().toDouble() == 34.76
            },
            EventRange(12.34, 34.76, includeUpper = false) to { fluxRecord ->
                fluxRecord.getValueByKey("range").toString() == "[12.34 : 34.76)"
            },
            arrayOf(
                12.34,
                "here is the other test",
                Duration.ofMillis(123)
            ) to { fluxRecord ->
                fluxRecord.getValueByKey("number").toString().toDouble() == 12.34
                        && fluxRecord.getValueByKey("message") == "here is the other test"
                        && fluxRecord.getValueByKey("duration") == "PT0.123S"
            },
            listOf(12.34, 8765, "here is the other test", Duration.ofMillis(123)) to { fluxRecord ->
                fluxRecord.getValueByKey("number").toString().toDouble() == 12.34
                        && fluxRecord.getValueByKey("message") == "here is the other test"
                        && fluxRecord.getValueByKey("duration") == "PT0.123S"
            },
            MyTestObject() to { fluxRecord ->
                fluxRecord.getValueByKey("other") == "MyTestObject(property1=1243.65, property2=here is the test)"
            }
        )
        return values
    }

    private suspend fun requestEvents(): List<FluxRecord> {
        val result = client.getQueryKotlinApi().query(
            "from(bucket: \"${configuration.bucket}\") |> range(start: 0) |> group(columns: [\"_measurement\"]) "
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
