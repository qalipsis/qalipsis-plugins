package io.qalipsis.plugins.r2dbc.jasync.converters

import assertk.all
import assertk.assertThat
import assertk.assertions.*
import com.github.jasync.sql.db.general.ArrayRowData
import com.github.jasync.sql.db.general.MutableResultSet
import io.micrometer.core.instrument.Counter
import io.mockk.coEvery
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.qalipsis.api.steps.datasource.DatasourceRecord
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.relaxedMockk
import io.qalipsis.test.mockk.verifyOnce
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.runBlockingTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.atomic.AtomicLong

@WithMockk
internal class ResultSetSingleConverterTest {

    @RelaxedMockK
    lateinit var resultValuesConverter: ResultValuesConverter

    @RelaxedMockK
    lateinit var counter: Counter

    @Test
    @Timeout(2)
    internal fun `should convert without monitoring`() = runBlockingTest {
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
        val converter = ResultSetSingleConverter(
            resultValuesConverter, null
        )

        // when
        val converted = mutableListOf<DatasourceRecord<Map<String, Any?>>>()
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
        confirmVerified(counter)
    }

    @ExperimentalCoroutinesApi
    @Test
    @Timeout(2)
    internal fun `should deserialize and count the records`() = runBlockingTest {
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
        val converter = ResultSetSingleConverter(
            resultValuesConverter, counter
        )

        // when
        val converted = mutableListOf<DatasourceRecord<Map<String, Any?>>>()
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
        verifyOnce {
            counter.increment(2.0)
        }
        confirmVerified(counter)
    }

}
