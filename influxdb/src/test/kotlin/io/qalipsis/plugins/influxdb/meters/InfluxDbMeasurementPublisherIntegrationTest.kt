/*
 * Copyright 2024 AERIS IT Solutions GmbH
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

package io.qalipsis.plugins.influxdb.meters

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.influxdb.client.InfluxDBClientOptions
import com.influxdb.client.kotlin.InfluxDBClientKotlinFactory
import com.influxdb.query.FluxRecord
import io.aerisconsulting.catadioptre.coInvokeInvisible
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.qalipsis.api.lang.concurrentList
import io.qalipsis.api.meters.Counter
import io.qalipsis.api.meters.DistributionMeasurementMetric
import io.qalipsis.api.meters.DistributionSummary
import io.qalipsis.api.meters.Gauge
import io.qalipsis.api.meters.MeasurementMetric
import io.qalipsis.api.meters.Meter
import io.qalipsis.api.meters.MeterSnapshot
import io.qalipsis.api.meters.MeterType
import io.qalipsis.api.meters.Statistic
import io.qalipsis.api.meters.Timer
import io.qalipsis.plugins.influxdb.AbstractInfluxDbIntegrationTest
import io.qalipsis.test.mockk.WithMockk
import kotlinx.coroutines.delay
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

@WithMockk
@Testcontainers
internal class InfluxDbMeasurementPublisherIntegrationTest : AbstractInfluxDbIntegrationTest() {

    @BeforeAll
    internal fun setUp() {
        configuration = InfluxDbMeasurementConfiguration()
            .apply {
                publishers = 1
                userName = "user"
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
                        configuration.userName,
                        configuration.password.toCharArray()
                    )
                    .org(configuration.org)
                    .build()
            )
    }

    private lateinit var configuration: InfluxDbMeasurementConfiguration

    @Test
    @Timeout(30)
    internal fun `should export data`() = testDispatcherProvider.run {
        // given
        val publisher = InfluxdbMeasurementPublisher(
            configuration = configuration,
            this,
        )
        publisher.init()
        val counterMock = mockk<Counter> {
            every { id } returns mockk<Meter.Id> {
                every { meterName } returns "my counter"
                every { tags } returns emptyMap()
                every { type } returns MeterType.COUNTER
                every { scenarioName } returns "SCENARIO one"
                every { campaignKey } returns "first campaign 5473653"
                every { stepName } returns "step uno"
            }
        }
        val timerMock = mockk<Timer> {
            every { id } returns mockk<Meter.Id> {
                every { meterName } returns "my timer"
                every { tags } returns emptyMap()
                every { type } returns MeterType.TIMER
                every { scenarioName } returns "SCENARIO two"
                every { campaignKey } returns "second campaign 47628233"
                every { stepName } returns "step dos"
            }
        }
        val gaugeMock = mockk<Gauge> {
            every { id } returns mockk<Meter.Id> {
                every { meterName } returns "my gauge"
                every { tags } returns mapOf("foo" to "bar", "cafe" to "one")
                every { type } returns MeterType.GAUGE
                every { scenarioName } returns "SCENARIO three"
                every { campaignKey } returns "third CAMPAIGN 7624839"
                every { stepName } returns "step tres"
            }
        }
        val summaryMock = mockk<DistributionSummary> {
            every { id } returns mockk<Meter.Id> {
                every { meterName } returns "my final summary"
                every { tags } returns mapOf("dist" to "summary", "local" to "host")
                every { type } returns MeterType.DISTRIBUTION_SUMMARY
                every { scenarioName } returns "scenario four"
                every { campaignKey } returns "fourth CAMPAIGN 283239"
                every { stepName } returns "step quart"
            }
        }
        val now = getTimeMock()
        val meterSnapshots = listOf(mockk<MeterSnapshot<Counter>> {
            every { timestamp } returns now
            every { meter } returns counterMock
            every { measurements } returns listOf(MeasurementMetric(8.0, Statistic.COUNT))
        }, mockk<MeterSnapshot<Gauge>> {
            every { timestamp } returns now
            every { meter } returns gaugeMock
            every { measurements } returns listOf(MeasurementMetric(5.0, Statistic.VALUE))
        }, mockk<MeterSnapshot<Timer>> {
            every { timestamp } returns now
            every { meter } returns timerMock
            every { measurements } returns listOf(
                MeasurementMetric(224.0, Statistic.MEAN),
                MeasurementMetric(178713.0, Statistic.TOTAL_TIME),
                MeasurementMetric(54328.5, Statistic.MAX),
                DistributionMeasurementMetric(500000448.5, Statistic.PERCENTILE, 85.0),
                DistributionMeasurementMetric(5432844.5, Statistic.PERCENTILE, 50.0),
            )
        }, mockk<MeterSnapshot<DistributionSummary>> {
            every { timestamp } returns now
            every { meter } returns summaryMock
            every { measurements } returns listOf(
                MeasurementMetric(70.0, Statistic.COUNT),
                MeasurementMetric(17873213.0, Statistic.TOTAL),
                MeasurementMetric(548.5, Statistic.MAX),
                DistributionMeasurementMetric(548.5, Statistic.PERCENTILE, 85.0),
                DistributionMeasurementMetric(54328.5, Statistic.PERCENTILE, 50.0),
            )
        })

        // when
        publisher.coInvokeInvisible<Void>("performPublish", meterSnapshots)

        // then
        val result = requestEvents()
        assertEquals(4, result.map { it.table }.distinct().count())

        //verification for counter
        val counterRecords =
            result.filter { it.getValueByKey("_measurement") == "qalipsis.first-campaign-5473653.scenario-one.step-uno.my-counter" }
        assertEquals(counterRecords.size, 1)
        counterRecords.forEach { measurement ->
            assertThat(measurement.getValueByKey("metric_type")).isEqualTo("counter")
            assertThat(measurement.getValueByKey("_field")).isEqualTo("count")
            assertThat(measurement.getValueByKey("_value")).isEqualTo(8.0)
        }

        //verification for gauge
        val gaugeRecords =
            result.filter { it.getValueByKey("_measurement") == "qalipsis.third-campaign-7624839.scenario-three.step-tres.my-gauge" }
        assertEquals(gaugeRecords.size, 1)
        gaugeRecords.forEach { measurement ->
            assertThat(measurement.getValueByKey("metric_type")).isEqualTo("gauge")
            assertThat(measurement.getValueByKey("_field")).isEqualTo("value")
            assertThat(measurement.getValueByKey("_value")).isEqualTo(5.0)
            assertThat(measurement.getValueByKey("cafe")).isEqualTo("\"one\"")
            assertThat(measurement.getValueByKey("foo")).isEqualTo("\"bar\"")
        }

        //verification for timer
        val timerRecords =
            result.filter { it.getValueByKey("_measurement") == "qalipsis.second-campaign-47628233.scenario-two.step-dos.my-timer" }
        assertEquals(timerRecords.size, 5)

        assertThat(timerRecords[0].getValueByKey("_field")).isEqualTo("max")
        assertThat(timerRecords[0].getValueByKey("_value")).isEqualTo(54328.5)
        assertThat(timerRecords[1].getValueByKey("_field")).isEqualTo("mean")
        assertThat(timerRecords[1].getValueByKey("_value")).isEqualTo(224.0)
        assertThat(timerRecords[2].getValueByKey("_field")).isEqualTo("percentile_50.0")
        assertThat(timerRecords[2].getValueByKey("_value")).isEqualTo(5432844.5)
        assertThat(timerRecords[3].getValueByKey("_field")).isEqualTo("percentile_85.0")
        assertThat(timerRecords[3].getValueByKey("_value")).isEqualTo(500000448.5)
        assertThat(timerRecords[4].getValueByKey("_field")).isEqualTo("total_time")
        assertThat(timerRecords[4].getValueByKey("_value")).isEqualTo(178713.0)
        timerRecords.forEach { measurement ->
            assertThat(measurement.getValueByKey("metric_type")).isEqualTo("timer")
        }

        //verification for summary
        val summaryRecords =
            result.filter { it.getValueByKey("_measurement") == "qalipsis.fourth-campaign-283239.scenario-four.step-quart.my-final-summary" }
        assertEquals(summaryRecords.size, 5)
        assertThat(summaryRecords[0].getValueByKey("_field")).isEqualTo("count")
        assertThat(summaryRecords[0].getValueByKey("_value")).isEqualTo(70.0)
        assertThat(summaryRecords[1].getValueByKey("_field")).isEqualTo("max")
        assertThat(summaryRecords[1].getValueByKey("_value")).isEqualTo(548.5)
        assertThat(summaryRecords[2].getValueByKey("_field")).isEqualTo("percentile_50.0")
        assertThat(summaryRecords[2].getValueByKey("_value")).isEqualTo(54328.5)
        assertThat(summaryRecords[3].getValueByKey("_field")).isEqualTo("percentile_85.0")
        assertThat(summaryRecords[3].getValueByKey("_value")).isEqualTo(548.5)
        assertThat(summaryRecords[4].getValueByKey("_field")).isEqualTo("total")
        assertThat(summaryRecords[4].getValueByKey("_value")).isEqualTo(17873213.0)

        summaryRecords.forEach { measurement ->
            assertThat(measurement.getValueByKey("metric_type")).isEqualTo("summary")
            assertThat(measurement.getValueByKey("dist")).isEqualTo("\"summary\"")
            assertThat(measurement.getValueByKey("local")).isEqualTo("\"host\"")
        }

        publisher.stop()
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

    private fun getTimeMock(): Instant {
        val now = Instant.now()
        val fixedClock = Clock.fixed(now, ZoneId.systemDefault())
        mockkStatic(Clock::class)
        every { Clock.systemUTC() } returns fixedClock

        return now
    }

}