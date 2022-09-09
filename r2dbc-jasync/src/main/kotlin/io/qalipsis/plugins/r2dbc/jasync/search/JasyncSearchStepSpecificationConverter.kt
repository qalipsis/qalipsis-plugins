package io.qalipsis.plugins.r2dbc.jasync.search

import com.github.jasync.sql.db.ConnectionPoolConfiguration
import com.github.jasync.sql.db.SuspendingConnection
import com.github.jasync.sql.db.asSuspending
import io.micrometer.core.instrument.MeterRegistry
import io.qalipsis.api.Executors
import io.qalipsis.api.annotations.StepConverter
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.events.EventsLogger
import io.qalipsis.api.steps.StepCreationContext
import io.qalipsis.api.steps.StepSpecification
import io.qalipsis.api.steps.StepSpecificationConverter
import io.qalipsis.plugins.r2dbc.jasync.converters.JasyncResultSetConverter
import io.qalipsis.plugins.r2dbc.jasync.converters.ParametersConverter
import io.qalipsis.plugins.r2dbc.jasync.converters.ResultValuesConverter
import io.qalipsis.plugins.r2dbc.jasync.dialect.Dialect
import jakarta.inject.Named
import kotlinx.coroutines.CoroutineDispatcher

/**
 * [StepSpecificationConverter] from [JasyncSearchStepSpecificationImpl] to [JasyncSearchStep] for Jasync search operation.
 */
@StepConverter
internal class JasyncSearchStepSpecificationConverter(
    private val meterRegistry: MeterRegistry,
    private val eventsLogger: EventsLogger,
    private val parametersConverter: ParametersConverter,
    private val resultValuesConverter: ResultValuesConverter,
    @Named(Executors.IO_EXECUTOR_NAME) private val ioCoroutineDispatcher: CoroutineDispatcher
) : StepSpecificationConverter<JasyncSearchStepSpecificationImpl<*>> {

    override fun support(stepSpecification: StepSpecification<*, *, *>): Boolean {
        return stepSpecification is JasyncSearchStepSpecificationImpl<*>
    }

    override suspend fun <I, O> convert(creationContext: StepCreationContext<JasyncSearchStepSpecificationImpl<*>>) {
        val spec = creationContext.stepSpecification
        val dialect = spec.protocol!!.dialect
        val connectionsPoolFactory = buildConnectionsPoolFactory(dialect, buildConnectionConfiguration(spec))
        val stepId = spec.name

        @Suppress("UNCHECKED_CAST")
        val step = JasyncSearchStep(
            id = stepId,
            connectionsPoolFactory = connectionsPoolFactory,
            retryPolicy = spec.retryPolicy,
            queryFactory = spec.queryFactory as (suspend (ctx: StepContext<*, *>, input: I) -> String),
            parametersFactory = buildParameterFactory(
                spec.parametersFactory as (suspend (ctx: StepContext<*, *>, input: I) -> List<*>)
            ),
            converter = JasyncResultSetBatchConverter<I>(resultValuesConverter,
                eventsLogger = eventsLogger.takeIf { spec.monitoringConfig.events },
                meterRegistry = meterRegistry.takeIf { spec.monitoringConfig.meters }
            ) as JasyncResultSetConverter<ResultSetWrapper, Any?, I>
        )

        creationContext.createdStep(step)
    }

    fun <I> buildParameterFactory(
        parametersFactory: (suspend (ctx: StepContext<*, *>, input: I) -> List<*>)
    ): (suspend (ctx: StepContext<*, *>, input: I) -> List<*>) {
        return { ctx, input -> parametersFactory(ctx, input).map { parametersConverter.process(it) } }
    }

    private fun buildConnectionConfiguration(spec: JasyncSearchStepSpecificationImpl<*>): ConnectionPoolConfiguration {
        return ConnectionPoolConfiguration(
            host = spec.connection.host,
            port = spec.connection.port,
            database = spec.connection.database,
            username = spec.connection.username!!,
            password = spec.connection.password,
            maxActiveConnections = spec.connection.maxActiveConnections,
            maxIdleTime = spec.connection.maxIdleTime.toMillis(),
            maxPendingQueries = spec.connection.maxPendingQueries,
            connectionValidationInterval = spec.connection.connectionValidationInterval.toMillis(),
            connectionCreateTimeout = spec.connection.connectionCreateTimeout.toMillis(),
            connectionTestTimeout = spec.connection.connectionTestTimeout.toMillis(),
            queryTimeout = spec.connection.queryTimeout?.toMillis(),
            eventLoopGroup = spec.connection.eventLoopGroup,
            executionContext = spec.connection.executionContext,
            ssl = spec.connection.ssl,
            charset = spec.connection.charset,
            maximumMessageSize = spec.connection.maximumMessageSize,
            allocator = spec.connection.allocator,
            applicationName = spec.connection.applicationName,
            interceptors = spec.connection.interceptors,
            maxConnectionTtl = spec.connection.maxConnectionTtl?.toMillis(),
            coroutineDispatcher = ioCoroutineDispatcher
        )
    }

    private fun buildConnectionsPoolFactory(
        dialect: Dialect,
        config: ConnectionPoolConfiguration
    ): () -> SuspendingConnection {
        return {
            dialect.connectionBuilder(config).asSuspending
        }
    }

}
