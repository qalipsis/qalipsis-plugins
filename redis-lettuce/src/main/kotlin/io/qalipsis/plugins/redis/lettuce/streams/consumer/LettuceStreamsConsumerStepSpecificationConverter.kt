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