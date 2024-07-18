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

import assertk.all
import assertk.assertThat
import assertk.assertions.any
import assertk.assertions.each
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import com.influxdb.client.InfluxDBClientOptions
import com.influxdb.client.kotlin.InfluxDBClientKotlinFactory
import com.influxdb.query.FluxRecord
import io.aerisconsulting.catadioptre.coInvokeInvisible
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.qalipsis.api.lang.concurrentList
import io.qalipsis.api.meters.DistributionMeasurementMetric
import io.qalipsis.api.meters.MeasurementMetric
import io.qalipsis.api.meters.Meter
import io.qalipsis.api.meters.MeterSnapshot
import io.qalipsis.api.meters.MeterType
import io.qalipsis.api.meters.Statistic
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
        val now = Instant.now()
        val meterSnapshots = listOf(
            mockk<MeterSnapshot> {
                every { timestamp } returns now
                every { meterId } returns Meter.Id(
                    "my counter",
                    MeterType.COUNTER,
                    mapOf(
                        "scenario" to "first scenario",
                        "campaign" to "first campaign 5473653",
                        "step" to "step number one"
                    )
                )
                every { measurements } returns listOf(MeasurementMetric(8.0, Statistic.COUNT))
            },
            mockk<MeterSnapshot> {
                every { timestamp } returns now
                every { meterId } returns Meter.Id(
                    "my gauge",
                    MeterType.GAUGE,
                    mapOf(
                        "scenario" to "third scenario",
                        "campaign" to "third CAMPAIGN 7624839",
                        "step" to "step number three",
                        "foo" to "bar",
                        "any-tag" to "any-value"
                    )
                )
                every { measurements } returns listOf(MeasurementMetric(5.0, Statistic.VALUE))
            }, mockk<MeterSnapshot> {
                every { timestamp } returns now
                every { meterId } returns Meter.Id(
                    "my timer",
                    MeterType.TIMER,
                    mapOf(
                        "scenario" to "second scenario",
                        "campaign" to "second campaign 47628233",
                        "step" to "step number two",
                    )
                )
                every { measurements } returns listOf(
                    MeasurementMetric(80.0, Statistic.COUNT),
                    MeasurementMetric(224.0, Statistic.MEAN),
                    MeasurementMetric(178713.0, Statistic.TOTAL_TIME),
                    MeasurementMetric(54328.5, Statistic.MAX),
                    DistributionMeasurementMetric(500000448.5, Statistic.PERCENTILE, 85.0),
                    DistributionMeasurementMetric(5432844.5, Statistic.PERCENTILE, 50.0),
                )
            }, mockk<MeterSnapshot> {
                every { timestamp } returns now
                every { meterId } returns Meter.Id(
                    "my final summary",
                    MeterType.DISTRIBUTION_SUMMARY,
                    mapOf(
                        "scenario" to "fourth scenario",
                        "campaign" to "fourth CAMPAIGN 283239",
                        "step" to "step number four",
                        "dist" to "summary",
                        "local" to "host"
                    )
                )
                every { measurements } returns listOf(
                    MeasurementMetric(70.0, Statistic.COUNT),
                    MeasurementMetric(22.0, Statistic.MEAN),
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

        // verification for counter
        val counterRecords =
            result.filter { it.getValueByKey("_measurement") == "qalipsis.my-counter" }
        assertThat(counterRecords).all {
            hasSize(1)
            any {
                it.all {
                    it.transform { it.getValueByKey("metric_type") }.isEqualTo("counter")
                    it.transform { it.getValueByKey("_field") }.isEqualTo("count")
                    it.transform { it.getValueByKey("_value") }.isEqualTo(8.0)
                    it.transform { it.getValueByKey("campaign") }.isEqualTo("first campaign 5473653")
                    it.transform { it.getValueByKey("scenario") }.isEqualTo("first scenario")
                    it.transform { it.getValueByKey("step") }.isEqualTo("step number one")
                }
            }
        }

        // verification for gauge
        val gaugeRecords =
            result.filter { it.getValueByKey("_measurement") == "qalipsis.my-gauge" }
        assertThat(gaugeRecords).all {
            hasSize(1)
            any {
                it.all {
                    it.transform { it.getValueByKey("metric_type") }.isEqualTo("gauge")
                    it.transform { it.getValueByKey("_field") }.isEqualTo("value")
                    it.transform { it.getValueByKey("_value") }.isEqualTo(5.0)
                    it.transform { it.getValueByKey("campaign") }.isEqualTo("third CAMPAIGN 7624839")
                    it.transform { it.getValueByKey("scenario") }.isEqualTo("third scenario")
                    it.transform { it.getValueByKey("step") }.isEqualTo("step number three")
                    it.transform { it.getValueByKey("any-tag") }.isEqualTo("any-value")
                    it.transform { it.getValueByKey("foo") }.isEqualTo("bar")
                }
            }
        }

        // verification for timer
        val timerRecords =
            result.filter { it.getValueByKey("_measurement") == "qalipsis.my-timer" }
        assertThat(timerRecords).all {
            hasSize(6)
            each {
                it.transform { it.getValueByKey("metric_type") }.isEqualTo("timer")
                it.transform { it.getValueByKey("campaign") }.isEqualTo("second campaign 47628233")
                it.transform { it.getValueByKey("scenario") }.isEqualTo("second scenario")
                it.transform { it.getValueByKey("step") }.isEqualTo("step number two")
            }
            any {
                it.all {
                    it.transform { it.getValueByKey("_field") }.isEqualTo("max")
                    it.transform { it.getValueByKey("_value") }.isEqualTo(54328.5)
                }
            }
            any {
                it.all {
                    it.transform { it.getValueByKey("_field") }.isEqualTo("mean")
                    it.transform { it.getValueByKey("_value") }.isEqualTo(224.0)
                }
            }
            any {
                it.all {
                    it.transform { it.getValueByKey("_field") }.isEqualTo("total_time")
                    it.transform { it.getValueByKey("_value") }.isEqualTo(178713.0)
                }
            }
            any {
                it.all {
                    it.transform { it.getValueByKey("_field") }.isEqualTo("count")
                    it.transform { it.getValueByKey("_value") }.isEqualTo(80.0)
                }
            }
            any {
                it.all {
                    it.transform { it.getValueByKey("_field") }.isEqualTo("percentile_50.0")
                    it.transform { it.getValueByKey("_value") }.isEqualTo(5432844.5)
                }
            }
            any {
                it.all {
                    it.transform { it.getValueByKey("_field") }.isEqualTo("percentile_85.0")
                    it.transform { it.getValueByKey("_value") }.isEqualTo(500000448.5)
                }
            }
        }

        // verification for summary
        val summaryRecords =
            result.filter { it.getValueByKey("_measurement") == "qalipsis.my-final-summary" }
        assertThat(summaryRecords).all {
            hasSize(6)
            each {
                it.transform { it.getValueByKey("metric_type") }.isEqualTo("summary")
                it.transform { it.getValueByKey("campaign") }.isEqualTo("fourth CAMPAIGN 283239")
                it.transform { it.getValueByKey("scenario") }.isEqualTo("fourth scenario")
                it.transform { it.getValueByKey("step") }.isEqualTo("step number four")
                it.transform { it.getValueByKey("dist") }.isEqualTo("summary")
                it.transform { it.getValueByKey("local") }.isEqualTo("host")
            }
            any {
                it.all {
                    it.transform { it.getValueByKey("_field") }.isEqualTo("max")
                    it.transform { it.getValueByKey("_value") }.isEqualTo(548.5)
                }
            }
            any {
                it.all {
                    it.transform { it.getValueByKey("_field") }.isEqualTo("mean")
                    it.transform { it.getValueByKey("_value") }.isEqualTo(22.0)
                }
            }
            any {
                it.all {
                    it.transform { it.getValueByKey("_field") }.isEqualTo("total")
                    it.transform { it.getValueByKey("_value") }.isEqualTo(17873213.0)
                }
            }
            any {
                it.all {
                    it.transform { it.getValueByKey("_field") }.isEqualTo("count")
                    it.transform { it.getValueByKey("_value") }.isEqualTo(70.0)
                }
            }
            any {
                it.all {
                    it.transform { it.getValueByKey("_field") }.isEqualTo("percentile_50.0")
                    it.transform { it.getValueByKey("_value") }.isEqualTo(54328.5)
                }
            }
            any {
                it.all {
                    it.transform { it.getValueByKey("_field") }.isEqualTo("percentile_85.0")
                    it.transform { it.getValueByKey("_value") }.isEqualTo(548.5)
                }
            }
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