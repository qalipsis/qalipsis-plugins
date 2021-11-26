package io.qalipsis.plugins.r2dbc.jasync.poll

import com.github.jasync.sql.db.ConnectionPoolConfiguration
import com.github.jasync.sql.db.ResultSet
import com.github.jasync.sql.db.SuspendingConnection
import com.github.jasync.sql.db.asSuspending
import io.aerisconsulting.catadioptre.KTestable
import io.micrometer.core.instrument.MeterRegistry
import io.qalipsis.api.Executors
import io.qalipsis.api.annotations.StepConverter
import io.qalipsis.api.events.EventsLogger
import io.qalipsis.api.steps.StepCreationContext
import io.qalipsis.api.steps.StepSpecification
import io.qalipsis.api.steps.StepSpecificationConverter
import io.qalipsis.api.steps.datasource.DatasourceObjectConverter
import io.qalipsis.api.steps.datasource.IterativeDatasourceStep
import io.qalipsis.api.steps.datasource.processors.NoopDatasourceObjectProcessor
import io.qalipsis.plugins.r2dbc.jasync.converters.ParametersConverter
import io.qalipsis.plugins.r2dbc.jasync.converters.ResultValuesConverter
import io.qalipsis.plugins.r2dbc.jasync.dialect.Dialect
import jakarta.inject.Named
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel


/**
 * [StepSpecificationConverter] from [JasyncPollStepSpecificationImpl] to [JasyncIterativeReader] for a data source.
 *
 * @author Eric Jessé
 */
@StepConverter
internal class JasyncPollStepSpecificationConverter(
    private val meterRegistry: MeterRegistry,
    private val eventsLogger: EventsLogger,
    private val parametersConverter: ParametersConverter,
    private val resultValuesConverter: ResultValuesConverter,
    @Named(Executors.IO_EXECUTOR_NAME) private val ioCoroutineScope: CoroutineScope,
    @Named(Executors.IO_EXECUTOR_NAME) private val ioCoroutineDispatcher: CoroutineDispatcher
) : StepSpecificationConverter<JasyncPollStepSpecificationImpl> {

    override fun support(stepSpecification: StepSpecification<*, *, *>): Boolean {
        return stepSpecification is JasyncPollStepSpecificationImpl
    }

    override suspend fun <I, O> convert(creationContext: StepCreationContext<JasyncPollStepSpecificationImpl>) {
        val spec = creationContext.stepSpecification
        val dialect = spec.protocol!!.dialect
        val sqlStatement = buildSqlStatement(dialect, spec)
        val connectionPoolFactory: () -> SuspendingConnection = {
            dialect.connectionBuilder(buildConnectionConfiguration(spec)).asSuspending
        }
        val stepId = spec.name
        val reader = JasyncIterativeReader(
            ioCoroutineScope,
            connectionPoolFactory,
            sqlStatement,
            spec.tieBreaker!!,
            spec.pollDelay!!,
            { Channel(Channel.UNLIMITED) }
        )

        val converter = buildConverter(stepId, spec)

        val step = IterativeDatasourceStep(
            stepId,
            reader,
            NoopDatasourceObjectProcessor(),
            converter
        )
        creationContext.createdStep(step)
    }

    @KTestable
    private fun buildSqlStatement(
        dialect: Dialect,
        spec: JasyncPollStepSpecificationImpl
    ): SqlPollStatement {
        return SqlPollStatementImpl(
            dialect = dialect,
            sql = spec.query!!,
            initialParameters = spec.parameters.map(parametersConverter::process),
            tieBreakerName = spec.tieBreaker!!,
            strictTieBreaker = spec.strictTieBreaker
        )
    }

    private fun buildConverter(
        stepId: String,
        spec: JasyncPollStepSpecificationImpl,
    ): DatasourceObjectConverter<ResultSet, out Any> {

        return if (spec.flattenOutput) {
            ResultSetSingleConverter(
                resultValuesConverter,
                eventsLogger = eventsLogger.takeIf { spec.monitoringConfig.events },
                meterRegistry = meterRegistry.takeIf { spec.monitoringConfig.meters }
            )
        } else {
            ResultSetBatchConverter(
                resultValuesConverter,
                eventsLogger = eventsLogger.takeIf { spec.monitoringConfig.events },
                meterRegistry = meterRegistry.takeIf { spec.monitoringConfig.meters })
        }
    }

    private fun buildConnectionConfiguration(spec: JasyncPollStepSpecificationImpl): ConnectionPoolConfiguration {
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
}
