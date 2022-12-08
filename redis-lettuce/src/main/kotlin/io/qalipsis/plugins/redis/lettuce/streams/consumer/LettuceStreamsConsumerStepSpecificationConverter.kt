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

package io.qalipsis.plugins.redis.lettuce.streams.consumer

import io.lettuce.core.StreamMessage
import io.qalipsis.api.Executors
import io.qalipsis.api.annotations.StepConverter
import io.qalipsis.api.events.EventsLogger
import io.qalipsis.api.meters.CampaignMeterRegistry
import io.qalipsis.api.steps.StepCreationContext
import io.qalipsis.api.steps.StepMonitoringConfiguration
import io.qalipsis.api.steps.StepSpecification
import io.qalipsis.api.steps.StepSpecificationConverter
import io.qalipsis.api.steps.datasource.DatasourceObjectConverter
import io.qalipsis.api.steps.datasource.IterativeDatasourceStep
import io.qalipsis.api.steps.datasource.processors.NoopDatasourceObjectProcessor
import io.qalipsis.plugins.redis.lettuce.configuration.RedisStatefulConnectionFactory
import jakarta.inject.Named
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope

/**
 * [StepSpecificationConverter] from [LettuceStreamsConsumerStepSpecificationImpl] to [LettuceStreamsIterativeReader] for a data
 * source.
 *
 * @author Gabriel Moraes
 */
@StepConverter
internal class LettuceStreamsConsumerStepSpecificationConverter(
    private val meterRegistry: CampaignMeterRegistry,
    private val eventsLogger: EventsLogger,
    @Named(Executors.IO_EXECUTOR_NAME) private val ioCoroutineScope: CoroutineScope,
    @Named(Executors.IO_EXECUTOR_NAME) private val ioCoroutineDispatcher: CoroutineDispatcher
) : StepSpecificationConverter<LettuceStreamsConsumerStepSpecificationImpl> {

    override fun support(stepSpecification: StepSpecification<*, *, *>): Boolean {
        return stepSpecification is LettuceStreamsConsumerStepSpecificationImpl
    }

    override suspend fun <I, O> convert(creationContext: StepCreationContext<LettuceStreamsConsumerStepSpecificationImpl>) {
        val spec = creationContext.stepSpecification
        val connectionFactory = suspend {
            RedisStatefulConnectionFactory(spec.connection).create()
        }

        val reader = LettuceStreamsIterativeReader(
            ioCoroutineScope = ioCoroutineScope,
            ioCoroutineDispatcher = ioCoroutineDispatcher,
            connectionFactory = connectionFactory,
            groupName = spec.groupName,
            concurrency = spec.concurrency,
            streamKey = spec.streamKey,
            offset = spec.offset.value,
            eventsLogger = eventsLogger
        )

        val step = IterativeDatasourceStep(
            spec.name,
            reader,
            NoopDatasourceObjectProcessor(),
            buildConverter(spec.monitoringConfig, spec.flattenOutput)
        )
        creationContext.createdStep(step)
    }

    private fun buildConverter(
        monitoringConfig: StepMonitoringConfiguration,
        flattenOutput: Boolean
    ): DatasourceObjectConverter<List<StreamMessage<ByteArray, ByteArray>>, out Any?> {

        return if (flattenOutput) {
            LettuceStreamsConsumerSingleConverter(
                meterRegistry = meterRegistry.takeIf { monitoringConfig.meters })
        } else {
            LettuceStreamsConsumerBatchConverter(
                meterRegistry = meterRegistry.takeIf { monitoringConfig.meters }
            )
        }
    }
}