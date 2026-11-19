/*
 * QALIPSIS
 * Copyright (C) 2025 AERIS IT Solutions GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
