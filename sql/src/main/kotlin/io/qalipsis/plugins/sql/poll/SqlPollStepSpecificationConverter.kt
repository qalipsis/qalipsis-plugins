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

package io.qalipsis.plugins.sql.poll

import io.aerisconsulting.catadioptre.KTestable
import io.qalipsis.api.Executors
import io.qalipsis.api.annotations.StepConverter
import io.qalipsis.api.events.EventsLogger
import io.qalipsis.api.meters.CampaignMeterRegistry
import io.qalipsis.api.steps.StepCreationContext
import io.qalipsis.api.steps.StepSpecification
import io.qalipsis.api.steps.StepSpecificationConverter
import io.qalipsis.api.steps.datasource.DatasourceObjectConverter
import io.qalipsis.api.steps.datasource.IterativeDatasourceStep
import io.qalipsis.api.steps.datasource.processors.NoopDatasourceObjectProcessor
import io.qalipsis.plugins.sql.SqlResultSet
import io.qalipsis.plugins.sql.converters.ParametersConverter
import io.qalipsis.plugins.sql.converters.ResultValuesConverter
import io.qalipsis.plugins.sql.dialect.Dialect
import jakarta.inject.Named
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel


/**
 * [StepSpecificationConverter] from [SqlPollStepSpecificationImpl] to [SqlIterativeReader] for a data source.
 *
 * @author Eric Jessé
 */
@StepConverter
internal class SqlPollStepSpecificationConverter(
    private val meterRegistry: CampaignMeterRegistry,
    private val eventsLogger: EventsLogger,
    private val parametersConverter: ParametersConverter,
    private val resultValuesConverter: ResultValuesConverter,
    @Named(Executors.IO_EXECUTOR_NAME) private val ioCoroutineScope: CoroutineScope,
) : StepSpecificationConverter<SqlPollStepSpecificationImpl> {

    override fun support(stepSpecification: StepSpecification<*, *, *>): Boolean {
        return stepSpecification is SqlPollStepSpecificationImpl
    }

    override suspend fun <I, O> convert(creationContext: StepCreationContext<SqlPollStepSpecificationImpl>) {
        val spec = creationContext.stepSpecification
        val dialect = spec.protocol!!.dialect
        val sqlStatement = buildSqlStatement(dialect, spec)
        val connectionPoolFactory = { dialect.createConnectionPool(spec.connection) }
        val stepId = spec.name
        val reader = SqlIterativeReader(
            ioCoroutineScope,
            connectionPoolFactory,
            dialect,
            sqlStatement,
            spec.pollDelay!!,
            { Channel(Channel.UNLIMITED) }
        )

        val converter = buildConverter(spec)

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
        spec: SqlPollStepSpecificationImpl
    ): SqlPollStatement {
        return SqlPollStatementImpl(
            dialect = dialect,
            sql = spec.query!!,
            initialParameters = spec.parameters.map(parametersConverter::process)
        )
    }

    private fun buildConverter(spec: SqlPollStepSpecificationImpl): DatasourceObjectConverter<SqlResultSet, out Any> {
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
}
