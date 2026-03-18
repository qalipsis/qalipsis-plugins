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

package io.qalipsis.plugins.sql.poll

import assertk.all
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.isSameInstanceAs
import assertk.assertions.isTrue
import io.aerisconsulting.catadioptre.getProperty
import io.aerisconsulting.catadioptre.invokeInvisible
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.mockk
import io.mockk.spyk
import io.qalipsis.api.steps.StepCreationContext
import io.qalipsis.api.steps.StepCreationContextImpl
import io.qalipsis.api.steps.datasource.DatasourceObjectConverter
import io.qalipsis.api.steps.datasource.IterativeDatasourceStep
import io.qalipsis.api.steps.datasource.processors.NoopDatasourceObjectProcessor
import io.qalipsis.plugins.sql.SqlResultSet
import io.qalipsis.plugins.sql.converters.ParametersConverter
import io.qalipsis.plugins.sql.converters.ResultValuesConverter
import io.qalipsis.plugins.sql.dialect.Dialect
import io.qalipsis.plugins.sql.dialect.Protocol
import io.qalipsis.plugins.sql.poll.catadioptre.buildSqlStatement
import io.qalipsis.test.assertk.prop
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.relaxedMockk
import io.qalipsis.test.mockk.verifyOnce
import io.qalipsis.test.steps.AbstractStepSpecificationConverterTest
import java.time.Duration
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import assertk.assertions.isNull as isNull1

/**
 *
 * @author Eric Jesse
 */
@WithMockk
internal class SqlPollStepSpecificationConverterTest :
    AbstractStepSpecificationConverterTest<SqlPollStepSpecificationConverter>() {

    @JvmField
    @RegisterExtension
    val testDispatcherProvider = TestDispatcherProvider()

    @RelaxedMockK
    lateinit var ioCoroutineScope: CoroutineScope

    @RelaxedMockK
    lateinit var parametersConverter: ParametersConverter

    @RelaxedMockK
    lateinit var resultValuesConverter: ResultValuesConverter

    @Test
    override fun `should not support unexpected spec`() {
        Assertions.assertFalse(converter.support(relaxedMockk()))
    }

    @Test
    override fun `should support expected spec`() {
        Assertions.assertTrue(converter.support(relaxedMockk<SqlPollStepSpecificationImpl>()))
    }

    @Test
    @ExperimentalCoroutinesApi
    fun `should convert with name`() = testDispatcherProvider.runTest {
        // given
        val spec = SqlPollStepSpecificationImpl()
        spec.apply {
            this.name = "my-step"
            protocol(Protocol.MARIADB)
            connection {
                port = 1234
                database = "my-database"
                username = "my-username"
            }
            query("This is my query")
            parameters(123, "my-param", true, LocalDate.of(2020, 11, 13))
            pollDelay(Duration.ofSeconds(23))
            broadcast(123, Duration.ofSeconds(20))
        }

        val creationContext = StepCreationContextImpl(scenarioSpecification, directedAcyclicGraph, spec)
        val spiedConverter = spyk(converter, recordPrivateCalls = true)

        val recordsConverter: DatasourceObjectConverter<SqlResultSet, out Any> = relaxedMockk()
        every { spiedConverter["buildConverter"](refEq(spec)) } returns recordsConverter

        val sqlPollStatement: SqlPollStatement = relaxedMockk()
        every {
            spiedConverter.buildSqlStatement(
                refEq(Protocol.MARIADB.dialect),
                refEq(spec)
            )
        } returns sqlPollStatement

        // when
        spiedConverter.convert<Unit, Map<String, *>>(
            creationContext as StepCreationContext<SqlPollStepSpecificationImpl>
        )

        // then
        creationContext.createdStep!!.let {
            assertThat(it).isInstanceOf(IterativeDatasourceStep::class).all {
                prop("name").isEqualTo("my-step")
                prop("reader").isNotNull().isInstanceOf(SqlIterativeReader::class).all {
                    prop("connectionPoolFactory").isNotNull()
                    prop("ioCoroutineScope").isSameInstanceAs(ioCoroutineScope)
                    prop("sqlPollStatement").isSameInstanceAs(sqlPollStatement)
                    prop("pollDelay").isEqualTo(Duration.ofSeconds(23))
                    prop("resultsChannelFactory").isNotNull()
                }
                prop("processor").isNotNull().isInstanceOf(NoopDatasourceObjectProcessor::class)
                prop("converter").isNotNull().isSameInstanceAs(recordsConverter)
            }
        }
        verifyOnce { spiedConverter["buildConverter"](refEq(spec)) }

        val channelFactory = creationContext.createdStep!!
            .getProperty<SqlIterativeReader>("reader")
            .getProperty<() -> Channel<SqlResultSet>>("resultsChannelFactory")
        val createdChannel = channelFactory()
        assertThat(createdChannel).all {
            transform { it.isEmpty }.isTrue()
            transform { it.isClosedForReceive }.isFalse()
            transform { it.isClosedForSend }.isFalse()
        }
    }

    @Test
    internal fun `should build the sql poll statement without parameters and non strict tie-breaker`() {
        // given
        val dialect: Dialect = relaxedMockk()
        val spec = SqlPollStepSpecificationImpl().also {
            it.query("select * from myTable order by myTieBreaker")
        }
        // Returns a mock for each converted parameter.
        val convertedParams = mutableListOf<Any>()
        every { parametersConverter.process(any()) } answers {
            mockk<Any>().also {
                convertedParams.add(it)
            }
        }

        // when
        val statement = converter.buildSqlStatement(dialect, spec)

        // then
        assertThat(statement).isInstanceOf(SqlPollStatementImpl::class).all {
            prop("dialect").isSameInstanceAs(dialect)
            prop("sql").isEqualTo("select * from myTable order by myTieBreaker")
            prop("initialParameters").isNotNull().isInstanceOf(List::class).hasSize(0)
            prop("tieBreakerName").isEqualTo("myTieBreaker")
            prop("tieBreakerOperator").isEqualTo(">=")
        }
    }

    @Test
    internal fun `should build the sql poll statement with parameters and strict tie-breaker`() {
        // given
        val dialect: Dialect = relaxedMockk()
        val spec = SqlPollStepSpecificationImpl().also {
            it.query("select * from myTable order by myTieBreaker")
            it.parameters(123, "my-param", true, LocalDate.of(2020, 11, 13))
        }

        // Returns a mock for each converted parameter.
        val convertedParams = mutableListOf<Any>()
        every { parametersConverter.process(any()) } answers {
            mockk<Any>().also {
                convertedParams.add(it)
            }
        }

        // when
        val statement = converter.buildSqlStatement(dialect, spec)

        // then
        assertThat(statement).isInstanceOf(SqlPollStatementImpl::class).all {
            prop("dialect").isSameInstanceAs(dialect)
            prop("sql").isEqualTo("select * from myTable order by myTieBreaker")
            prop("initialParameters").isNotNull().isInstanceOf(List::class).all {
                hasSize(4)
                containsExactly(*convertedParams.toTypedArray())
            }
            prop("tieBreakerName").isEqualTo("myTieBreaker")
            prop("tieBreakerOperator").isEqualTo(">=")
        }
    }

    @Test
    internal fun `should build batch converter with eventsLogger`() {
        // given
        val spec = SqlPollStepSpecificationImpl()
        spec.monitoring { meters = false }

        // when
        val converter =
            converter.invokeInvisible<DatasourceObjectConverter<SqlResultSet, out Any>>("buildConverter", spec)

        // then
        assertThat(converter).isInstanceOf(ResultSetBatchConverter::class).all {
            prop("resultValuesConverter").isSameInstanceAs(resultValuesConverter)
            prop("meterRegistry").isNull1()
            prop("eventsLogger").isNotNull().isEqualTo(eventsLogger)
        }
    }

    @Test
    internal fun `should build batch converter with monitoring`() {
        // given
        val spec = SqlPollStepSpecificationImpl()
        spec.monitoring { events = false }
        // when
        val converter =
            converter.invokeInvisible<DatasourceObjectConverter<SqlResultSet, out Any>>("buildConverter", spec)

        // then
        assertThat(converter).isInstanceOf(ResultSetBatchConverter::class).all {
            prop("resultValuesConverter").isSameInstanceAs(resultValuesConverter)
            prop("meterRegistry").isNotNull().isEqualTo(meterRegistry)
            prop("eventsLogger").isNull1()
        }
    }


    @Test
    internal fun `should build single converter with eventsLogger`() {
        // given
        val spec = SqlPollStepSpecificationImpl()
        spec.flattenOutput = true
        spec.monitoring { meters = false }
        // when
        val converter =
            converter.invokeInvisible<DatasourceObjectConverter<SqlResultSet, out Any>>("buildConverter", spec)

        // then
        assertThat(converter).isInstanceOf(ResultSetSingleConverter::class).all {
            prop("resultValuesConverter").isSameInstanceAs(resultValuesConverter)
            prop("eventsLogger").isNotNull().isEqualTo(eventsLogger)
            prop("meterRegistry").isNull1()
        }
    }

    @Test
    internal fun `should build single converter with monitoring`() {
        // given
        val spec = SqlPollStepSpecificationImpl()
        spec.flattenOutput = true
        spec.monitoring { events = false }
        // when
        val converter =
            converter.invokeInvisible<DatasourceObjectConverter<SqlResultSet, out Any>>("buildConverter", spec)

        // then
        assertThat(converter).isInstanceOf(ResultSetSingleConverter::class).all {
            prop("resultValuesConverter").isSameInstanceAs(resultValuesConverter)
            prop("eventsLogger").isNull1()
            prop("meterRegistry").isNotNull().isEqualTo(meterRegistry)
        }
    }
}
