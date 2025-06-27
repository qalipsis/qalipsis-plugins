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

package io.qalipsis.plugins.jms.producer

import io.qalipsis.api.annotations.StepConverter
import io.qalipsis.api.events.EventsLogger
import io.qalipsis.api.meters.CampaignMeterRegistry
import io.qalipsis.api.steps.StepCreationContext
import io.qalipsis.api.steps.StepSpecification
import io.qalipsis.api.steps.StepSpecificationConverter
import kotlinx.coroutines.ExperimentalCoroutinesApi

/**
 * [StepSpecificationConverter] from [JmsProducerStepSpecificationImpl] to [JmsProducerStep]
 * to use the Produce API.
 *
 * @author Alexander Sosnovsky
 */
@ExperimentalCoroutinesApi
@StepConverter
internal class JmsProducerStepSpecificationConverter(
    private val meterRegistry: CampaignMeterRegistry,
    private val eventsLogger: EventsLogger
) : StepSpecificationConverter<JmsProducerStepSpecificationImpl<*>> {

    override fun support(stepSpecification: StepSpecification<*, *, *>): Boolean {
        return stepSpecification is JmsProducerStepSpecificationImpl
    }

    override suspend fun <I, O> convert(creationContext: StepCreationContext<JmsProducerStepSpecificationImpl<*>>) {
        val spec = creationContext.stepSpecification
        val stepId = spec.name
        val producer = JmsProducer(spec.connectionFactory, JmsProducerConverter(),
            eventsLogger = eventsLogger.takeIf { spec.monitoringConfig.events },
            meterRegistry = meterRegistry.takeIf { spec.monitoringConfig.meters }
        )

        @Suppress("UNCHECKED_CAST")
        val step = JmsProducerStep(
            stepId = stepId,
            retryPolicy = spec.retryPolicy,
            recordFactory = spec.recordsFactory,
            jmsProducer = producer,
        )
        creationContext.createdStep(step)
    }
}
