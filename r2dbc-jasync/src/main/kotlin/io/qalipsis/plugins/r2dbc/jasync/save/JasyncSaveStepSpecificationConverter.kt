package io.qalipsis.plugins.r2dbc.jasync.save

import com.github.jasync.sql.db.ConnectionPoolConfiguration
import com.github.jasync.sql.db.SuspendingConnection
import com.github.jasync.sql.db.asSuspending
import io.aerisconsulting.catadioptre.KTestable
import io.micrometer.core.instrument.MeterRegistry
import io.qalipsis.api.Executors
import io.qalipsis.api.annotations.StepConverter
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.context.StepName
import io.qalipsis.api.lang.supplyIf
import io.qalipsis.api.steps.StepCreationContext
import io.qalipsis.api.steps.StepSpecification
import io.qalipsis.api.steps.StepSpecificationConverter
import io.qalipsis.plugins.r2dbc.jasync.dialect.Dialect
import jakarta.inject.Named
import kotlinx.coroutines.CoroutineDispatcher

/**
 * [StepSpecificationConverter] from [JasyncSaveStepSpecificationImpl] to [JasyncSaveStep] for Jasync save operation.
 *
 * @author Carlos Vieira
 */
@StepConverter
internal class JasyncSaveStepSpecificationConverter(
    @Named(Executors.IO_EXECUTOR_NAME) private val ioCoroutineDispatcher: CoroutineDispatcher,
    private val meterRegistry: MeterRegistry,
) : StepSpecificationConverter<JasyncSaveStepSpecificationImpl<*>> {

    override fun support(stepSpecification: StepSpecification<*, *, *>): Boolean {
        return stepSpecification is JasyncSaveStepSpecificationImpl
    }

    override suspend fun <I, O> convert(creationContext: StepCreationContext<JasyncSaveStepSpecificationImpl<*>>) {
        val spec = creationContext.stepSpecification
        val stepId = spec.name
        val jasyncMetrics = buildMetrics(spec.metrics, stepId)
        val dialect = spec.protocol!!.dialect
        val connectionsPoolBuilder = buildConnectionsPoolBuilder(dialect, buildConnectionConfiguration(spec))

        @Suppress("UNCHECKED_CAST")
        val step = JasyncSaveStep(
            id = stepId,
            retryPolicy = spec.retryPolicy,
            connectionPoolBuilder = connectionsPoolBuilder,
            dialect,
            tableNameFactory = spec.tableNameFactory,
            columnsFactory = spec.columnsFactory,
            recordsFactory = spec.rowsFactory as suspend (StepContext<*, *>, I) -> List<JasyncSaverRecord>,
            metrics = jasyncMetrics
        )
        creationContext.createdStep(step)
    }

    @KTestable
    private fun buildMetrics(metrics: JasyncSaveMetricsConfiguration, stepId: StepName): JasyncQueryMetrics {
        val recordsCounter = supplyIf(metrics.recordsCount) {
            meterRegistry.counter("jasync-save-records", "step", stepId)
        }

        val failureCounter = supplyIf(metrics.failureCount) {
            meterRegistry.counter("jasync-save-records-failure", "step", stepId)
        }

        val successCounter = supplyIf(metrics.successCount) {
            meterRegistry.counter("jasync-save-records-success", "step", stepId)
        }

        val timeToSuccess = supplyIf(metrics.timeToSuccess) {
            meterRegistry.timer("jasync-save-records-time-to-success", "step", stepId)
        }

        val timeToFailure = supplyIf(metrics.timeToFailure) {
            meterRegistry.timer("jasync-save-records-time-to-failure", "step", stepId)
        }

        return JasyncQueryMetrics(
            recordsCount = recordsCounter,
            failureCounter = failureCounter,
            successCounter = successCounter,
            timeToSuccess = timeToSuccess,
            timeToFailure = timeToFailure
        )
    }

    private fun buildConnectionConfiguration(spec: JasyncSaveStepSpecificationImpl<*>): ConnectionPoolConfiguration {
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

    @KTestable
    private fun buildConnectionsPoolBuilder(dialect: Dialect, config: ConnectionPoolConfiguration): () -> SuspendingConnection {
        return {
            dialect.connectionBuilder(config).asSuspending
        }
    }


}
