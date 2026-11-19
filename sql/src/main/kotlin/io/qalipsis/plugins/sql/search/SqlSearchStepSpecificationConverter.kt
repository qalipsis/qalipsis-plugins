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

package io.qalipsis.plugins.sql.search

import io.aerisconsulting.catadioptre.KTestable
import io.qalipsis.api.annotations.StepConverter
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.events.EventsLogger
import io.qalipsis.api.meters.CampaignMeterRegistry
import io.qalipsis.api.steps.StepCreationContext
import io.qalipsis.api.steps.StepSpecification
import io.qalipsis.api.steps.StepSpecificationConverter
import io.qalipsis.plugins.sql.SqlConnection
import io.qalipsis.plugins.sql.converters.ParametersConverter
import io.qalipsis.plugins.sql.converters.ResultValuesConverter
import io.qalipsis.plugins.sql.converters.SqlResultSetConverter
import io.qalipsis.plugins.sql.dialect.Dialect
import io.r2dbc.pool.ConnectionPool

/**
 * [StepSpecificationConverter] from [SqlSearchStepSpecificationImpl] to [SqlSearchStep] for SQL search operation.
 */
@StepConverter
internal class SqlSearchStepSpecificationConverter(
    private val meterRegistry: CampaignMeterRegistry,
    private val eventsLogger: EventsLogger,
    private val parametersConverter: ParametersConverter,
    private val resultValuesConverter: ResultValuesConverter,
) : StepSpecificationConverter<SqlSearchStepSpecificationImpl<*>> {

    override fun support(stepSpecification: StepSpecification<*, *, *>): Boolean {
        return stepSpecification is SqlSearchStepSpecificationImpl<*>
    }

    override suspend fun <I, O> convert(creationContext: StepCreationContext<SqlSearchStepSpecificationImpl<*>>) {
        val spec = creationContext.stepSpecification
        val dialect = spec.protocol!!.dialect
        val connectionsPoolFactory = buildConnectionsPoolFactory(dialect, spec.connection)
        val stepId = spec.name

        @Suppress("UNCHECKED_CAST")
        val step = SqlSearchStep(
            id = stepId,
            connectionPoolFactory = connectionsPoolFactory,
            dialect = dialect,
            retryPolicy = spec.retryPolicy,
            queryFactory = spec.queryFactory as (suspend (ctx: StepContext<*, *>, input: I) -> String),
            parametersFactory = buildParameterFactory(
                spec.parametersFactory as (suspend (ctx: StepContext<*, *>, input: I) -> List<*>)
            ),
            converter = SqlResultSetBatchConverter<I>(resultValuesConverter,
                eventsLogger = eventsLogger.takeIf { spec.monitoringConfig.events },
                meterRegistry = meterRegistry.takeIf { spec.monitoringConfig.meters }
            ) as SqlResultSetConverter<ResultSetWrapper, Any?, I>
        )

        creationContext.createdStep(step)
    }

    fun <I> buildParameterFactory(
        parametersFactory: (suspend (ctx: StepContext<*, *>, input: I) -> List<*>)
    ): (suspend (ctx: StepContext<*, *>, input: I) -> List<*>) {
        return { ctx, input -> parametersFactory(ctx, input).map { parametersConverter.process(it) } }
    }

    @KTestable
    private fun buildConnectionsPoolFactory(
        dialect: Dialect,
        connection: SqlConnection
    ): () -> ConnectionPool {
        return {
            dialect.createConnectionPool(connection)
        }
    }
}
