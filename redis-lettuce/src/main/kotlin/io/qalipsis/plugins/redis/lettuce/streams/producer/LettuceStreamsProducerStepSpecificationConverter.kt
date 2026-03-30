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

package io.qalipsis.plugins.redis.lettuce.streams.producer

import io.qalipsis.api.Executors
import io.qalipsis.api.annotations.StepConverter
import io.qalipsis.api.events.EventsLogger
import io.qalipsis.api.meters.CampaignMeterRegistry
import io.qalipsis.api.steps.StepCreationContext
import io.qalipsis.api.steps.StepSpecification
import io.qalipsis.api.steps.StepSpecificationConverter
import io.qalipsis.api.sync.asSuspended
import io.qalipsis.plugins.redis.lettuce.configuration.RedisStatefulConnectionFactory
import jakarta.inject.Named
import java.time.Duration
import kotlin.coroutines.CoroutineContext

/**
 * [StepSpecificationConverter] from [LettuceStreamsProducerStepSpecificationImpl] to [LettuceStreamsProducerStep].
 *
 * @author Gabriel Moraes
 */
@StepConverter
internal class LettuceStreamsProducerStepSpecificationConverter(
    private val eventsLogger: EventsLogger,
    private val meterRegistry: CampaignMeterRegistry,
    @Named(Executors.IO_EXECUTOR_NAME) private val ioCoroutineContext: CoroutineContext
) : StepSpecificationConverter<LettuceStreamsProducerStepSpecificationImpl<*>> {

    override fun support(stepSpecification: StepSpecification<*, *, *>): Boolean {
        return stepSpecification is LettuceStreamsProducerStepSpecificationImpl<*>
    }

    override suspend fun <I, O> convert(creationContext: StepCreationContext<LettuceStreamsProducerStepSpecificationImpl<*>>) {
        val spec = creationContext.stepSpecification
        val connectionFactory = suspend {
            RedisStatefulConnectionFactory(spec.connection).create().asSuspended().get(
                CONNECTION_TIMEOUT
            )
        }

        val step = LettuceStreamsProducerStep(
            id = spec.name,
            retryPolicy = spec.retryPolicy,
            ioCoroutineContext = ioCoroutineContext,
            connectionFactory = connectionFactory,
            recordsFactory = spec.recordsFactory,
            eventsLogger = eventsLogger.takeIf { spec.monitoringConfig.events },
            meterRegistry = meterRegistry.takeIf { spec.monitoringConfig.meters }
        )

        creationContext.createdStep(step)
    }

    companion object {
        private val CONNECTION_TIMEOUT = Duration.ofSeconds(10)
    }
}