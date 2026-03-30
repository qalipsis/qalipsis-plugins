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

package io.qalipsis.plugins.sql.save

import io.aerisconsulting.catadioptre.KTestable
import io.qalipsis.api.annotations.StepConverter
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.events.EventsLogger
import io.qalipsis.api.meters.CampaignMeterRegistry
import io.qalipsis.api.steps.StepCreationContext
import io.qalipsis.api.steps.StepSpecification
import io.qalipsis.api.steps.StepSpecificationConverter
import io.qalipsis.plugins.sql.SqlConnection
import io.qalipsis.plugins.sql.dialect.Dialect
import io.r2dbc.pool.ConnectionPool

/**
 * [StepSpecificationConverter] from [SqlSaveStepSpecificationImpl] to [SqlSaveStep] for SQL save operation.
 *
 * @author Carlos Vieira
 */
@StepConverter
internal class SqlSaveStepSpecificationConverter(
    private val meterRegistry: CampaignMeterRegistry,
    private val eventsLogger: EventsLogger,
) : StepSpecificationConverter<SqlSaveStepSpecificationImpl<*>> {

    override fun support(stepSpecification: StepSpecification<*, *, *>): Boolean {
        return stepSpecification is SqlSaveStepSpecificationImpl
    }

    override suspend fun <I, O> convert(creationContext: StepCreationContext<SqlSaveStepSpecificationImpl<*>>) {
        val spec = creationContext.stepSpecification
        val stepId = spec.name
        val dialect = spec.protocol!!.dialect
        val connectionsPoolFactory = buildConnectionsPoolFactory(dialect, spec.connection)

        @Suppress("UNCHECKED_CAST")
        val step = SqlSaveStep(
            id = stepId,
            retryPolicy = spec.retryPolicy,
            connectionPoolFactory = connectionsPoolFactory,
            dialect,
            tableNameFactory = spec.tableNameFactory,
            columnsFactory = spec.columnsFactory,
            recordsFactory = spec.rowsFactory as suspend (StepContext<*, *>, I) -> List<SqlSaveRecord>,
            eventsLogger = eventsLogger.takeIf { spec.monitoringConfig.events },
            meterRegistry = meterRegistry.takeIf { spec.monitoringConfig.meters }
        )
        creationContext.createdStep(step)
    }

    @KTestable
    private fun buildConnectionsPoolFactory(dialect: Dialect, connection: SqlConnection): () -> ConnectionPool {
        return {
            dialect.createConnectionPool(connection)
        }
    }
}
