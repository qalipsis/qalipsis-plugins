/*
 * Copyright 2022 AERIS IT Solutions GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package io.qalipsis.plugins.influxdb.poll

import com.influxdb.client.InfluxDBClientOptions
import com.influxdb.client.kotlin.InfluxDBClientKotlinFactory
import io.micrometer.core.instrument.MeterRegistry
import io.qalipsis.api.Executors
import io.qalipsis.api.annotations.StepConverter
import io.qalipsis.api.events.EventsLogger
import io.qalipsis.api.lang.supplyIf
import io.qalipsis.api.steps.StepCreationContext
import io.qalipsis.api.steps.StepSpecification
import io.qalipsis.api.steps.StepSpecificationConverter
import io.qalipsis.api.steps.datasource.DatasourceObjectConverter
import io.qalipsis.api.steps.datasource.IterativeDatasourceStep
import io.qalipsis.api.steps.datasource.processors.NoopDatasourceObjectProcessor
import io.qalipsis.plugins.influxdb.poll.converters.InfluxDbDocumentPollBatchConverter
import io.qalipsis.plugins.influxdb.poll.converters.InfluxDbDocumentPollSingleConverter
import jakarta.inject.Named
import kotlinx.coroutines.CoroutineScope

/**
 * [StepSpecificationConverter] from [InfluxDbPollStepSpecificationImpl] to [InfluxDbIterativeReader] for a data source.
 *
 * @author Alexander Sosnovsky
 */
@StepConverter
internal class InfluxDbPollStepSpecificationConverter(
    private val meterRegistry: MeterRegistry,
    private val eventsLogger: EventsLogger,
    @Named(Executors.IO_EXECUTOR_NAME) private val coroutineScope: CoroutineScope
) : StepSpecificationConverter<InfluxDbPollStepSpecificationImpl> {

    override fun support(stepSpecification: StepSpecification<*, *, *>): Boolean {
        return stepSpecification is InfluxDbPollStepSpecificationImpl
    }

    override suspend fun <I, O> convert(creationContext: StepCreationContext<InfluxDbPollStepSpecificationImpl>) {
        val spec = creationContext.stepSpecification
        val stepId = spec.name

        val reader = InfluxDbIterativeReader(
            clientFactory = {
                InfluxDBClientKotlinFactory.create(
                    InfluxDBClientOptions.builder()
                        .url(spec.connectionConfiguration.url)
                        .authenticate(
                            spec.connectionConfiguration.user,
                            spec.connectionConfiguration.password.toCharArray()
                        )
                        .org(spec.connectionConfiguration.org)
                        .build()
                )
            },
            coroutineScope = coroutineScope,
            pollStatement = InfluxDbPollStatement(spec.query),
            pollDelay = spec.pollPeriod,
            eventsLogger = supplyIf(spec.monitoringConfiguration.events) { eventsLogger },
            meterRegistry = supplyIf(spec.monitoringConfiguration.meters) { meterRegistry }
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

    private fun buildConverter(spec: InfluxDbPollStepSpecificationImpl): DatasourceObjectConverter<InfluxDbQueryResult, out Any> {
        return if (spec.flattenOutput) {
            InfluxDbDocumentPollSingleConverter()
        } else {
            InfluxDbDocumentPollBatchConverter()
        }
    }
}
