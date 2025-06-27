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

package io.qalipsis.plugins.r2dbc.jasync.save

import com.github.jasync.sql.db.ConnectionPoolConfiguration
import com.github.jasync.sql.db.SuspendingConnection
import com.github.jasync.sql.db.asSuspending
import io.aerisconsulting.catadioptre.KTestable
import io.qalipsis.api.Executors
import io.qalipsis.api.annotations.StepConverter
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.events.EventsLogger
import io.qalipsis.api.meters.CampaignMeterRegistry
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
    private val meterRegistry: CampaignMeterRegistry,
    private val eventsLogger: EventsLogger,
) : StepSpecificationConverter<JasyncSaveStepSpecificationImpl<*>> {

    override fun support(stepSpecification: StepSpecification<*, *, *>): Boolean {
        return stepSpecification is JasyncSaveStepSpecificationImpl
    }

    override suspend fun <I, O> convert(creationContext: StepCreationContext<JasyncSaveStepSpecificationImpl<*>>) {
        val spec = creationContext.stepSpecification
        val stepId = spec.name
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
            recordsFactory = spec.rowsFactory as suspend (StepContext<*, *>, I) -> List<JasyncSaveRecord>,
            eventsLogger = eventsLogger.takeIf { spec.monitoringConfig.events },
            meterRegistry = meterRegistry.takeIf { spec.monitoringConfig.meters }
        )
        creationContext.createdStep(step)
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
