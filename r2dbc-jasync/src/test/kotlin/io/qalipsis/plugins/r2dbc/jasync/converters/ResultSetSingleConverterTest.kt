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

package io.qalipsis.plugins.r2dbc.jasync.converters

import assertk.all
import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.index
import assertk.assertions.isEqualTo
import assertk.assertions.isTrue
import assertk.assertions.key
import assertk.assertions.prop
import com.github.jasync.sql.db.general.ArrayRowData
import com.github.jasync.sql.db.general.MutableResultSet
import io.mockk.coEvery
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.mockk
import io.qalipsis.api.context.MonitoringTags
import io.qalipsis.api.context.StepStartStopContext
import io.qalipsis.api.events.EventsLogger
import io.qalipsis.api.meters.CampaignMeterRegistry
import io.qalipsis.api.meters.Counter
import io.qalipsis.api.meters.Meter
import io.qalipsis.api.steps.datasource.DatasourceRecord
import io.qalipsis.plugins.r2dbc.jasync.poll.ResultSetSingleConverter
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.relaxedMockk
import io.qalipsis.test.mockk.verifyOnce
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.extension.RegisterExtension
import java.util.concurrent.atomic.AtomicLong

@WithMockk
internal class ResultSetSingleConverterTest {

    @JvmField
    @RegisterExtension
    val testDispatcherProvider = TestDispatcherProvider()

    @RelaxedMockK
    lateinit var resultValuesConverter: ResultValuesConverter

    private val counter = relaxedMockk<Counter>()

    private val eventsLogger = relaxedMockk<EventsLogger>()

    private val successCounter = relaxedMockk<Counter>()

    private val failureCounter = relaxedMockk<Counter>()

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    @Timeout(5)
    internal fun `should convert without monitoring`() = testDispatcherProvider.runTest {
        // given
        every { resultValuesConverter.process(any()) } answers { "converted_${firstArg<String>()}" }
        val value1FromRecord1 = "1_1"
        val value2FromRecord1 = "1_2"
        val value1FromRecord2 = "2_1"
        val value2FromRecord2 = "2_2"
        val mapping = mapOf("col-1" to 0, "col-2" to 1)
        val resultSet = MutableResultSet(
            columnTypes = listOf(relaxedMockk {
                every { name } returns "col-1"
            }, relaxedMockk {
                every { name } returns "col-2"
            }),
            rows = mutableListOf(
                ArrayRowData(0, mapping, arrayOf(value1FromRecord1, value2FromRecord1)),
                ArrayRowData(1, mapping, arrayOf(value1FromRecord2, value2FromRecord2))
            )
        )
        val channel = Channel<DatasourceRecord<Map<String, Any?>>>(2)
        val startStopContext = relaxedMockk<StepStartStopContext>()
        val converter = ResultSetSingleConverter(
            resultValuesConverter, null, null
        )

        // when
        val converted = mutableListOf<DatasourceRecord<Map<String, Any?>>>()
        converter.start(startStopContext)
        converter.supply(
            AtomicLong(123),
            resultSet,
            relaxedMockk { coEvery { send(any()) } coAnswers { channel.send(firstArg()) } })
        converted.add(channel.receive())
        converted.add(channel.receive())

        // then
        assertThat(channel.isEmpty).isTrue()
        assertThat(converted).all {
            index(0).all {
                prop(DatasourceRecord<Map<String, Any?>>::ordinal).isEqualTo(123L)
                prop(DatasourceRecord<Map<String, Any?>>::value).all {
                    hasSize(2)
                    key("col-1").isEqualTo("converted_1_1")
                    key("col-2").isEqualTo("converted_1_2")
                }
            }
            index(1).all {
                prop(DatasourceRecord<Map<String, Any?>>::ordinal).isEqualTo(124L)
                prop(DatasourceRecord<Map<String, Any?>>::value).all {
                    hasSize(2)
                    key("col-1").isEqualTo("converted_2_1")
                    key("col-2").isEqualTo("converted_2_2")
                }
            }
        }
        confirmVerified(counter, eventsLogger)
    }

    @ExperimentalCoroutinesApi
    @Test
    @Timeout(4)
    internal fun `should deserialize and count the records`() = testDispatcherProvider.runTest {
        // given
        every { resultValuesConverter.process(any()) } answers { "converted_${firstArg<String>()}" }
        val value1FromRecord1 = "1_1"
        val value2FromRecord1 = "1_2"
        val value1FromRecord2 = "2_1"
        val value2FromRecord2 = "2_2"
        val mapping = mapOf("col-1" to 0, "col-2" to 1)
        val resultSet = MutableResultSet(
            columnTypes = listOf(relaxedMockk {
                every { name } returns "col-1"
            }, relaxedMockk {
                every { name } returns "col-2"
            }),
            rows = mutableListOf(
                ArrayRowData(0, mapping, arrayOf(value1FromRecord1, value2FromRecord1)),
                ArrayRowData(1, mapping, arrayOf(value1FromRecord2, value2FromRecord2))
            )
        )
        val channel = Channel<DatasourceRecord<Map<String, Any?>>>(2)
        val metersTags: Map<String, String> = mockk()

        val meterRegistry = relaxedMockk<CampaignMeterRegistry> {
            every {
                counter(
                    "scenario-test",
                    "step-test",
                    "r2dbc-jasync-poll-records",
                    refEq(metersTags)
                )
            } returns counter
            every { counter.report(any()) } returns counter
            every {
                counter(
                    "scenario-test",
                    "step-test",
                    "r2dbc-jasync-poll-successes",
                    refEq(metersTags)
                )
            } returns successCounter
            every { successCounter.report(any()) } returns successCounter
            every {
                counter(
                    "scenario-test",
                    "step-test",
                    "r2dbc-jasync-poll-failures",
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
        val converter = ResultSetSingleConverter(
            resultValuesConverter, meterRegistry, eventsLogger
        )
        val eventsTags: Map<String, String> = mockk()

        // when
        val converted = mutableListOf<DatasourceRecord<Map<String, Any?>>>()
        converter.start(startStopContext)
        converter.supply(
            AtomicLong(123),
            resultSet,
            relaxedMockk(moreInterfaces = *arrayOf(MonitoringTags::class)) {
                coEvery { send(any()) } coAnswers { channel.send(firstArg()) }
                every { (this@relaxedMockk as MonitoringTags).toEventTags() } returns eventsTags
            })
        converted.add(channel.receive())
        converted.add(channel.receive())

        // then
        assertThat(channel.isEmpty).isTrue()
        assertThat(converted).all {
            index(0).all {
                prop(DatasourceRecord<Map<String, Any?>>::ordinal).isEqualTo(123L)
                prop(DatasourceRecord<Map<String, Any?>>::value).all {
                    hasSize(2)
                    key("col-1").isEqualTo("converted_1_1")
                    key("col-2").isEqualTo("converted_1_2")
                }
            }
            index(1).all {
                prop(DatasourceRecord<Map<String, Any?>>::ordinal).isEqualTo(124L)
                prop(DatasourceRecord<Map<String, Any?>>::value).all {
                    hasSize(2)
                    key("col-1").isEqualTo("converted_2_1")
                    key("col-2").isEqualTo("converted_2_2")
                }
            }
        }
        verifyOnce {
            counter.increment(2.0)
            counter.report(any<Meter.ReportingConfiguration<Counter>.() -> Unit>())
            eventsLogger.info("r2dbc.jasync.poll.records", 2, any(), tags = refEq(eventsTags))
        }
        confirmVerified(counter, eventsLogger)
    }

}
