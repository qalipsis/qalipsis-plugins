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

package io.qalipsis.plugins.redis.lettuce.poll

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
import io.qalipsis.api.sync.asSuspended
import io.qalipsis.plugins.redis.lettuce.configuration.RedisStatefulConnectionFactory
import io.qalipsis.plugins.redis.lettuce.poll.converters.PollResultSetBatchConverter
import io.qalipsis.plugins.redis.lettuce.poll.converters.PollResultSetSingleConverter
import io.qalipsis.plugins.redis.lettuce.poll.converters.RedisToJavaConverter
import jakarta.inject.Named
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import java.time.Duration

/**
 * [StepSpecificationConverter] from [LettucePollStepSpecificationImpl] to [LettuceIterativeReader] for a data source.
 *
 * @author Gabriel Moraes
 */
@StepConverter
internal class LettucePollStepSpecificationConverter(
    private val meterRegistry: CampaignMeterRegistry,
    private val redisToJavaConverter: RedisToJavaConverter,
    private val eventsLogger: EventsLogger,
    @Named(Executors.IO_EXECUTOR_NAME) private val ioCoroutineScope: CoroutineScope
) : StepSpecificationConverter<LettucePollStepSpecificationImpl<*>> {

    override fun support(stepSpecification: StepSpecification<*, *, *>): Boolean {
        return stepSpecification is LettucePollStepSpecificationImpl<*>
    }

    override suspend fun <I, O> convert(creationContext: StepCreationContext<LettucePollStepSpecificationImpl<*>>) {
        val spec = creationContext.stepSpecification
        val connectionFactory = suspend {
            RedisStatefulConnectionFactory(spec.connection).create().asSuspended().get(DEFAULT_TIMEOUT)
        }

        val stepId = spec.name

        val redisScanExecutor = LettuceScanExecutorFactory.newInstance(
            spec.redisMethod,
            eventsLogger.takeIf { spec.monitoringConfig.events }
        )

        val reader = LettuceIterativeReader(
            ioCoroutineScope,
            connectionFactory,
            spec.pollDelay!!,
            spec.keyOrPattern,
            redisScanExecutor,
        ) { Channel(Channel.UNLIMITED) }

        val converter = buildConverter(spec)

        val step = IterativeDatasourceStep(
            stepId,
            reader,
            NoopDatasourceObjectProcessor(),
            converter
        )
        creationContext.createdStep(step)
    }

    private fun buildConverter(spec: LettucePollStepSpecificationImpl<*>): DatasourceObjectConverter<PollRawResult<*>, out Any> {
        return if (spec.flattenOutput) {
            PollResultSetSingleConverter(
                redisToJavaConverter,
                eventsLogger = eventsLogger.takeIf { spec.monitoringConfig.events },
                meterRegistry = meterRegistry.takeIf { spec.monitoringConfig.meters },
                spec.redisMethod.name.lowercase()
            )
        } else {
            PollResultSetBatchConverter(
                redisToJavaConverter,
                eventsLogger = eventsLogger.takeIf { spec.monitoringConfig.events },
                meterRegistry = meterRegistry.takeIf { spec.monitoringConfig.meters },
                spec.redisMethod.name.lowercase()
            )
        }
    }

    companion object {
        /**
         * Timeout used by default when connecting to redis.
         */
        private val DEFAULT_TIMEOUT = Duration.ofSeconds(10)
    }
}
