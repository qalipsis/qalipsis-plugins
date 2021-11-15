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
internal class ResultSetBatchConverterTest {

    @RelaxedMockK
    lateinit var resultValuesConverter: ResultValuesConverter

    @RelaxedMockK
    lateinit var counter: Counter

    @Test
    @Timeout(5)
    @ExperimentalCoroutinesApi
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
        val channel = Channel<List<DatasourceRecord<Map<String, Any?>>>>(1)
        val converter = ResultSetBatchConverter(
            resultValuesConverter, null
        )

        // when
        converter.supply(
            AtomicLong(123),
            resultSet,
            relaxedMockk { coEvery { send(any()) } coAnswers { channel.send(firstArg()) } })
        val converted = channel.receive()

        // then
        assertThat(converted).all {
            hasSize(2)
            index(0).all {
                prop(DatasourceRecord<Map<String, Any?>>::ordinal).isEqualTo(123L)
                prop(DatasourceRecord<Map<String, Any?>>::value).all {
                    key("col-1").isEqualTo("converted_1_1")
                    key("col-2").isEqualTo("converted_1_2")
                }
            }
            index(1).all {
                prop(DatasourceRecord<Map<String, Any?>>::ordinal).isEqualTo(124L)
                prop(DatasourceRecord<Map<String, Any?>>::value).all {
                    key("col-1").isEqualTo("converted_2_1")
                    key("col-2").isEqualTo("converted_2_2")
                }
            }
        }
        confirmVerified(counter)
    }

    @Test
    @Timeout(5)
    @ExperimentalCoroutinesApi
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
        val channel = Channel<List<DatasourceRecord<Map<String, Any?>>>>(1)
        val converter = ResultSetBatchConverter(
            resultValuesConverter, counter
        )

        // when
        converter.supply(
            AtomicLong(123),
            resultSet,
            relaxedMockk { coEvery { send(any()) } coAnswers { channel.send(firstArg()) } })
        val converted = channel.receive()

        // then
        assertThat(converted).all {
            hasSize(2)
            index(0).all {
                prop(DatasourceRecord<Map<String, Any?>>::ordinal).isEqualTo(123L)
                prop(DatasourceRecord<Map<String, Any?>>::value).all {
                    key("col-1").isEqualTo("converted_1_1")
                    key("col-2").isEqualTo("converted_1_2")
                }
            }
            index(1).all {
                prop(DatasourceRecord<Map<String, Any?>>::ordinal).isEqualTo(124L)
                prop(DatasourceRecord<Map<String, Any?>>::value).all {
                    key("col-1").isEqualTo("converted_2_1")
                    key("col-2").isEqualTo("converted_2_2")
                }
            }
        }
        verifyOnce { counter.increment(2.0) }
        confirmVerified(counter)
    }

}
