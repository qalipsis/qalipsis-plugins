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

package io.qalipsis.plugins.graphite.poll

import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.kotlinModule
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.qalipsis.api.Executors
import io.qalipsis.api.annotations.StepConverter
import io.qalipsis.api.events.EventsLogger
import io.qalipsis.api.lang.supplyIf
import io.qalipsis.api.meters.CampaignMeterRegistry
import io.qalipsis.api.steps.StepCreationContext
import io.qalipsis.api.steps.StepSpecification
import io.qalipsis.api.steps.StepSpecificationConverter
import io.qalipsis.api.steps.datasource.DatasourceObjectConverter
import io.qalipsis.api.steps.datasource.IterativeDatasourceStep
import io.qalipsis.api.steps.datasource.processors.NoopDatasourceObjectProcessor
import io.qalipsis.plugins.graphite.poll.converters.GraphitePollBatchConverter
import io.qalipsis.plugins.graphite.search.GraphiteQuery
import io.qalipsis.plugins.graphite.search.GraphiteRenderApiService
import jakarta.inject.Named
import kotlinx.coroutines.CoroutineScope
import java.util.*

/**
 * [StepSpecificationConverter] from [GraphitePollStepSpecificationImpl] to [GraphiteIterativeReader] for a data source.
 *
 * @author Teyyihan Aksu
 */
@StepConverter
internal class GraphitePollStepSpecificationConverter(
    private val meterRegistry: CampaignMeterRegistry,
    private val eventsLogger: EventsLogger,
    @Named(Executors.IO_EXECUTOR_NAME) private val coroutineScope: CoroutineScope
) : StepSpecificationConverter<GraphitePollStepSpecificationImpl> {

    override fun support(stepSpecification: StepSpecification<*, *, *>): Boolean {
        return stepSpecification is GraphitePollStepSpecificationImpl
    }

    override suspend fun <I, O> convert(creationContext: StepCreationContext<GraphitePollStepSpecificationImpl>) {
        val spec = creationContext.stepSpecification
        val stepId = spec.name
        val auth = "${spec.connectionConfiguration.username}:${spec.connectionConfiguration.password}"

        val reader = GraphiteIterativeReader(
            clientFactory = {
                GraphiteRenderApiService(
                    spec.connectionConfiguration.url,
                    jacksonObjectMapper().registerModules(kotlinModule {
                        configure(KotlinFeature.NullToEmptyCollection, true)
                        configure(KotlinFeature.NullToEmptyMap, true)
                        configure(KotlinFeature.NullIsSameAsDefault, true)
                    }, JavaTimeModule()),
                    HttpClient(CIO),
                    Base64.getUrlEncoder().encodeToString(auth.toByteArray())
                )
            },
            coroutineScope = coroutineScope,
            pollStatement = GraphitePollStatement(GraphiteQuery().also(spec.queryBuilder)),
            pollDelay = spec.pollPeriod,
            eventsLogger = supplyIf(spec.monitoringConfiguration.events) { eventsLogger },
            meterRegistry = supplyIf(spec.monitoringConfiguration.meters) { meterRegistry }
        )

        val converter = buildConverter()

        val step = IterativeDatasourceStep(
            stepId,
            reader,
            NoopDatasourceObjectProcessor(),
            converter
        )
        creationContext.createdStep(step)
    }

    private fun buildConverter(): DatasourceObjectConverter<GraphiteQueryResult, out Any> {
        return GraphitePollBatchConverter()
    }

}