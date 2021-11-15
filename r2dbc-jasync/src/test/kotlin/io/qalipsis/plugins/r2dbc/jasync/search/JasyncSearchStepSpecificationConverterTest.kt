package io.qalipsis.plugins.r2dbc.jasync.search

import assertk.all
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import com.github.jasync.sql.db.ConnectionPoolConfiguration
import com.github.jasync.sql.db.ResultSet
import com.github.jasync.sql.db.SuspendingConnection
import com.github.jasync.sql.db.SuspendingConnectionImpl
import io.aerisconsulting.catadioptre.invokeInvisible
import io.mockk.coVerify
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.spyk
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.steps.StepCreationContext
import io.qalipsis.api.steps.StepCreationContextImpl
import io.qalipsis.plugins.r2dbc.jasync.JasyncConnection
import io.qalipsis.plugins.r2dbc.jasync.converters.JasyncResultSetBatchConverter
import io.qalipsis.plugins.r2dbc.jasync.converters.JasyncResultSetConverter
import io.qalipsis.plugins.r2dbc.jasync.converters.JasyncResultSetSingleConverter
import io.qalipsis.plugins.r2dbc.jasync.converters.ParametersConverter
import io.qalipsis.plugins.r2dbc.jasync.converters.ResultValuesConverter
import io.qalipsis.plugins.r2dbc.jasync.dialect.Dialect
import io.qalipsis.plugins.r2dbc.jasync.dialect.DialectConfigurations
import io.qalipsis.plugins.r2dbc.jasync.dialect.Protocol
import io.qalipsis.test.assertk.prop
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.coVerifyOnce
import io.qalipsis.test.mockk.relaxedMockk
import io.qalipsis.test.mockk.verifyOnce
import io.qalipsis.test.steps.AbstractStepSpecificationConverterTest
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.test.runBlockingTest
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

/**
 * @author Fiodar Hmyza
 */
@WithMockk
@Suppress("UNCHECKED_CAST")
internal class JasyncSearchStepSpecificationConverterTest :
    AbstractStepSpecificationConverterTest<JasyncSearchStepSpecificationConverter>() {

    @RelaxedMockK
    lateinit var protocol: Protocol

    @RelaxedMockK
    lateinit var connection: JasyncConnection.() -> Unit

    @RelaxedMockK
    lateinit var connectionPoolConfiguration: ConnectionPoolConfiguration

    @RelaxedMockK
    lateinit var parametersConverter: ParametersConverter

    @RelaxedMockK
    lateinit var resultValuesConverter: ResultValuesConverter

    @RelaxedMockK
    lateinit var ioCoroutineDispatcher: CoroutineDispatcher

    @Test
    override fun `should not support unexpected spec`() {
        Assertions.assertFalse(converter.support(relaxedMockk()))
    }

    @Test
    override fun `should support expected spec`() {
        Assertions.assertTrue(converter.support(relaxedMockk<JasyncSearchStepSpecificationImpl<*>>()))
    }

    @Test
    fun `should convert with name and retry policy`() = runBlockingTest {
        // given
        val queryFactory: suspend (ctx: StepContext<*, *>, input: Int) -> String = relaxedMockk()
        val paramsFactory: suspend (ctx: StepContext<*, *>, input: Int) -> List<*> = relaxedMockk()
        val spec = JasyncSearchStepSpecificationImpl<Int>().also {
            it.name = "my-step"
            it.retry(mockedRetryPolicy)
            it.protocol(protocol)
            it.connection(connection)
            it.query(queryFactory)
            it.parameters(paramsFactory)
        }

        val spiedConverter = spyk(converter, recordPrivateCalls = true)
        val creationContext = StepCreationContextImpl(scenarioSpecification, directedAcyclicGraph, spec)
        val connectionsPoolFactory: () -> SuspendingConnection = { relaxedMockk() }
        val convertedParamsFactory: suspend (ctx: StepContext<*, *>, input: Int) -> List<*> = relaxedMockk()

        every { spiedConverter["buildConnectionConfiguration"](refEq(spec)) } returns connectionPoolConfiguration
        every {
            spiedConverter["buildConnectionsPoolFactory"](any<Dialect>(), refEq(connectionPoolConfiguration))
        } returns connectionsPoolFactory
        every { spiedConverter.buildParameterFactory(refEq(paramsFactory)) } returns convertedParamsFactory

        // when
        @Suppress("UNCHECKED_CAST")
        spiedConverter.convert<Int, Map<String, Any?>>(
            creationContext as StepCreationContext<JasyncSearchStepSpecificationImpl<*>>
        )

        // then
        creationContext.createdStep!!.let {
            assertThat(it).isInstanceOf(JasyncSearchStep::class).all {
                prop("id").isEqualTo("my-step")
                prop("connectionsPoolFactory").isEqualTo(connectionsPoolFactory)
                prop("retryPolicy").isEqualTo(mockedRetryPolicy)
                prop("parametersFactory").isEqualTo(convertedParamsFactory)
                prop("queryFactory").isEqualTo(queryFactory)
            }
        }

        verifyOnce { spiedConverter["buildConnectionConfiguration"](refEq(spec)) }
        verifyOnce { spiedConverter["buildConnectionsPoolFactory"](any<Dialect>(), refEq(connectionPoolConfiguration)) }
        verifyOnce { spiedConverter["buildConverter"](eq(spec.name.toString()), refEq(spec)) }
        verifyOnce { spiedConverter.buildParameterFactory(refEq(paramsFactory)) }
        coVerifyOnce {
            spiedConverter.convert<Int, Map<String, Any?>>(
                refEq(creationContext as StepCreationContext<JasyncSearchStepSpecificationImpl<*>>)
            )
        }
        confirmVerified(spiedConverter)
    }

    @Test
    fun `should convert without name and retry policy`() = runBlockingTest {
        // given
        val queryFactory: suspend (ctx: StepContext<*, *>, input: Int) -> String = relaxedMockk()
        val paramsFactory: suspend (ctx: StepContext<*, *>, input: Int) -> List<*> = relaxedMockk()
        val spec = JasyncSearchStepSpecificationImpl<Int>().also {
            it.protocol(protocol)
            it.connection(connection)
            it.query(queryFactory)
            it.parameters(paramsFactory)
        }

        val spiedConverter = spyk(converter, recordPrivateCalls = true)
        val creationContext = StepCreationContextImpl(scenarioSpecification, directedAcyclicGraph, spec)
        val connectionsPoolFactory: () -> SuspendingConnection = { relaxedMockk() }
        val convertedParamsFactory: suspend (ctx: StepContext<*, *>, input: Int) -> List<*> = relaxedMockk()

        every { spiedConverter["buildConnectionConfiguration"](refEq(spec)) } returns connectionPoolConfiguration
        every {
            spiedConverter["buildConnectionsPoolFactory"](any<Dialect>(), refEq(connectionPoolConfiguration))
        } returns connectionsPoolFactory
        every { spiedConverter.buildParameterFactory(refEq(paramsFactory)) } returns convertedParamsFactory

        // when
        spiedConverter.convert<Int, Map<String, Any?>>(
            creationContext as StepCreationContext<JasyncSearchStepSpecificationImpl<*>>
        )

        // then
        creationContext.createdStep!!.let {
            assertThat(it).isInstanceOf(JasyncSearchStep::class).all {
                prop("id").isNotNull()
                prop("connectionsPoolFactory").isEqualTo(connectionsPoolFactory)
                prop("retryPolicy").isNull()
                prop("parametersFactory").isEqualTo(convertedParamsFactory)
                prop("queryFactory").isEqualTo(queryFactory)
            }
        }

        verifyOnce { spiedConverter["buildConnectionConfiguration"](refEq(spec)) }
        verifyOnce { spiedConverter["buildConnectionsPoolFactory"](any<Dialect>(), refEq(connectionPoolConfiguration)) }
        verifyOnce { spiedConverter["buildConverter"](any<String>(), refEq(spec)) }
        verifyOnce { spiedConverter.buildParameterFactory(refEq(paramsFactory)) }
        coVerifyOnce {
            spiedConverter.convert<Int, Map<String, Any?>>(
                refEq(creationContext as StepCreationContext<JasyncSearchStepSpecificationImpl<*>>)
            )
        }
        confirmVerified(spiedConverter)
    }

    @Test
    fun `should build batch converter`() {
        // given
        val spec = JasyncSearchStepSpecificationImpl<Any>()

        // when
        val converter = converter.invokeInvisible<JasyncResultSetConverter<ResultSet, *, *>>("buildConverter","my-step", spec)

        // then
        assertThat(converter).isInstanceOf(JasyncResultSetBatchConverter::class).all {
            prop("resultValuesConverter").isEqualTo(resultValuesConverter)
        }
    }

    @Test
    fun `should build single converter`() {
        // given
        val spec = JasyncSearchStepSpecificationImpl<Any>()

        spec.flatten()

        // when
        val converter = converter.invokeInvisible<JasyncResultSetConverter<ResultSet, *, *>>("buildConverter","my-step", spec)

        // then
        assertThat(converter).isInstanceOf(JasyncResultSetSingleConverter::class).all {
            prop("resultValuesConverter").isEqualTo(resultValuesConverter)
        }
    }

    @Test
    fun `should build parameter builder`() = runBlockingTest {
        // given
        val context: StepContext<*, *> = relaxedMockk()
        val input: Any = relaxedMockk()
        val parametersFactory: suspend (ctx: StepContext<*, *>, input: Any) -> List<*> = { _, _ -> listOf(1, 2, 3) }

        // when
        val convertedParameterFactory = converter.invokeInvisible<(suspend (ctx: StepContext<*, *>, input: Any) -> List<*>)>("buildParameterFactory", parametersFactory)
        val parameters = convertedParameterFactory(context, input)

        // then
        coVerify(exactly = 3, verifyBlock = { parametersConverter.process(any()) })
        assertThat(parameters.size).isEqualTo(3)
    }

    @Test
    fun `should build connections pool builder`() {
        // given
        val dialect = DialectConfigurations.POSTGRESQL
        val configuration = ConnectionPoolConfiguration()

        // when
        val connectionsPoolFactory = converter.invokeInvisible<() -> SuspendingConnection>("buildConnectionsPoolFactory", dialect, configuration)
        val connection = connectionsPoolFactory()

        // then
        assertThat(connection).isInstanceOf(SuspendingConnectionImpl::class).all {
            prop("connection").isNotNull()
        }
    }

    @Test
    fun `should build connection configuration`() {
        // given
        val spec = JasyncSearchStepSpecificationImpl<Any>().also {
            it.connection {
                host = "my-server"
                port = 5678
                database = "my-other-database"
                username = "my-other-username"
                password = "my-other-password"
            }
        }

        // when
        val configuration = converter.invokeInvisible<ConnectionPoolConfiguration>("buildConnectionConfiguration", spec)

        // then
        assertThat(configuration).isInstanceOf(ConnectionPoolConfiguration::class).all {
            prop("host").isEqualTo("my-server")
            prop("port").isEqualTo(5678)
            prop("database").isEqualTo("my-other-database")
            prop("username").isEqualTo("my-other-username")
            prop("password").isEqualTo("my-other-password")
        }
    }

}
