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

package io.qalipsis.plugins.sql.search

import assertk.all
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import io.aerisconsulting.catadioptre.invokeInvisible
import io.mockk.coVerify
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.spyk
import io.qalipsis.plugins.sql.search.catadioptre.buildConnectionsPoolFactory
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.steps.StepCreationContext
import io.qalipsis.api.steps.StepCreationContextImpl
import io.qalipsis.plugins.sql.converters.ParametersConverter
import io.qalipsis.plugins.sql.converters.ResultValuesConverter
import io.qalipsis.plugins.sql.dialect.Protocol
import io.qalipsis.test.assertk.prop
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.coVerifyOnce
import io.qalipsis.test.mockk.relaxedMockk
import io.qalipsis.test.mockk.verifyOnce
import io.qalipsis.test.steps.AbstractStepSpecificationConverterTest
import io.r2dbc.pool.ConnectionPool
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

/**
 * @author Fiodar Hmyza
 */
@WithMockk
@Suppress("UNCHECKED_CAST")
internal class SqlSearchStepSpecificationConverterTest :
    AbstractStepSpecificationConverterTest<SqlSearchStepSpecificationConverter>() {

    @JvmField
    @RegisterExtension
    val testDispatcherProvider = TestDispatcherProvider()

    @RelaxedMockK
    lateinit var protocol: Protocol

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
        Assertions.assertTrue(converter.support(relaxedMockk<SqlSearchStepSpecificationImpl<*>>()))
    }

    @Test
    fun `should convert with name and retry policy`() = testDispatcherProvider.runTest {
        // given
        val queryFactory: suspend (ctx: StepContext<*, *>, input: Int) -> String = relaxedMockk()
        val paramsFactory: suspend (ctx: StepContext<*, *>, input: Int) -> List<*> = relaxedMockk()
        val spec = SqlSearchStepSpecificationImpl<Int>().also {
            it.name = "my-step"
            it.retry(mockedRetryPolicy)
            it.protocol(protocol)
            it.connection {
                host = "my-server"
                port = 5678
                database = "my-other-database"
                username = "my-other-username"
                password = "my-other-password"
            }
            it.query(queryFactory)
            it.parameters(paramsFactory)
        }

        val spiedConverter = spyk(converter, recordPrivateCalls = true)
        val creationContext = StepCreationContextImpl(scenarioSpecification, directedAcyclicGraph, spec)
        val connectionPoolFactory: () -> ConnectionPool = { relaxedMockk() }
        val convertedParamsFactory: suspend (ctx: StepContext<*, *>, input: Int) -> List<*> = relaxedMockk()

        every {
            spiedConverter.buildConnectionsPoolFactory(any(), refEq(spec.connection))
        } returns connectionPoolFactory
        every { spiedConverter.buildParameterFactory(refEq(paramsFactory)) } returns convertedParamsFactory

        // when
        @Suppress("UNCHECKED_CAST")
        spiedConverter.convert<Int, Map<String, Any?>>(
            creationContext as StepCreationContext<SqlSearchStepSpecificationImpl<*>>
        )

        // then
        creationContext.createdStep!!.let {
            assertThat(it).isInstanceOf(SqlSearchStep::class).all {
                prop("name").isEqualTo("my-step")
                prop("connectionPoolFactory").isEqualTo(connectionPoolFactory)
                prop("retryPolicy").isEqualTo(mockedRetryPolicy)
                prop("parametersFactory").isEqualTo(convertedParamsFactory)
                prop("queryFactory").isEqualTo(queryFactory)
                prop("converter").isNotNull().isInstanceOf(SqlResultSetBatchConverter::class)
            }
        }

        verifyOnce { spiedConverter.buildConnectionsPoolFactory(any(), refEq(spec.connection)) }
        verifyOnce { spiedConverter.buildParameterFactory(refEq(paramsFactory)) }
        coVerifyOnce {
            spiedConverter.convert<Int, Map<String, Any?>>(
                refEq(creationContext as StepCreationContext<SqlSearchStepSpecificationImpl<*>>)
            )
        }
        confirmVerified(spiedConverter)
    }

    @Test
    fun `should convert without name and retry policy`() = testDispatcherProvider.runTest {
        // given
        val queryFactory: suspend (ctx: StepContext<*, *>, input: Int) -> String = relaxedMockk()
        val paramsFactory: suspend (ctx: StepContext<*, *>, input: Int) -> List<*> = relaxedMockk()
        val spec = SqlSearchStepSpecificationImpl<Int>().also {
            it.protocol(protocol)
            it.connection {
                host = "my-server"
                port = 5678
                database = "my-other-database"
                username = "my-other-username"
                password = "my-other-password"
            }
            it.query(queryFactory)
            it.parameters(paramsFactory)
        }

        val spiedConverter = spyk(converter, recordPrivateCalls = true)
        val creationContext = StepCreationContextImpl(scenarioSpecification, directedAcyclicGraph, spec)
        val connectionPoolFactory: () -> ConnectionPool = { relaxedMockk() }
        val convertedParamsFactory: suspend (ctx: StepContext<*, *>, input: Int) -> List<*> = relaxedMockk()

        every {
            spiedConverter.buildConnectionsPoolFactory(any(), refEq(spec.connection))
        } returns connectionPoolFactory
        every { spiedConverter.buildParameterFactory(refEq(paramsFactory)) } returns convertedParamsFactory

        // when
        spiedConverter.convert<Int, Map<String, Any?>>(
            creationContext as StepCreationContext<SqlSearchStepSpecificationImpl<*>>
        )

        // then
        creationContext.createdStep!!.let {
            assertThat(it).isInstanceOf(SqlSearchStep::class).all {
                prop("name").isNotNull()
                prop("connectionPoolFactory").isEqualTo(connectionPoolFactory)
                prop("retryPolicy").isNull()
                prop("parametersFactory").isEqualTo(convertedParamsFactory)
                prop("queryFactory").isEqualTo(queryFactory)
                prop("converter").isNotNull().isInstanceOf(SqlResultSetBatchConverter::class)
            }
        }

        verifyOnce { spiedConverter.buildConnectionsPoolFactory(any(), refEq(spec.connection)) }
        verifyOnce { spiedConverter.buildParameterFactory(refEq(paramsFactory)) }
        coVerifyOnce {
            spiedConverter.convert<Int, Map<String, Any?>>(
                refEq(creationContext as StepCreationContext<SqlSearchStepSpecificationImpl<*>>)
            )
        }
        confirmVerified(spiedConverter)
    }

    @Test
    fun `should build parameter builder`() = testDispatcherProvider.runTest {
        // given
        val context: StepContext<*, *> = relaxedMockk()
        val input: Any = relaxedMockk()
        val parametersFactory: suspend (ctx: StepContext<*, *>, input: Any) -> List<*> = { _, _ -> listOf(1, 2, 3) }

        // when
        val convertedParameterFactory =
            converter.invokeInvisible<(suspend (ctx: StepContext<*, *>, input: Any) -> List<*>)>(
                "buildParameterFactory",
                parametersFactory
            )
        val parameters = convertedParameterFactory(context, input)

        // then
        coVerify(exactly = 3, verifyBlock = { parametersConverter.process(any()) })
        assertThat(parameters.size).isEqualTo(3)
    }

}
