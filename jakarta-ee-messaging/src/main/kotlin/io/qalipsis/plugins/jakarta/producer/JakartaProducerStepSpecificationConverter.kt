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

package io.qalipsis.plugins.jakarta.producer

import io.qalipsis.api.annotations.StepConverter
import io.qalipsis.api.events.EventsLogger
import io.qalipsis.api.meters.CampaignMeterRegistry
import io.qalipsis.api.steps.StepCreationContext
import io.qalipsis.api.steps.StepSpecification
import io.qalipsis.api.steps.StepSpecificationConverter
import kotlinx.coroutines.ExperimentalCoroutinesApi

/**
 * [StepSpecificationConverter] from [JakartaProducerStepSpecificationImpl] to [JakartaProducerStep]
 * to use the Produce API.
 *
 * @author Krawist Ngoben
 */
@ExperimentalCoroutinesApi
@StepConverter
internal class JakartaProducerStepSpecificationConverter(
    private val meterRegistry: CampaignMeterRegistry,
    private val eventsLogger: EventsLogger
) : StepSpecificationConverter<JakartaProducerStepSpecificationImpl<*>> {

    override fun support(stepSpecification: StepSpecification<*, *, *>): Boolean {
        return stepSpecification is JakartaProducerStepSpecificationImpl
    }

    override suspend fun <I, O> convert(creationContext: StepCreationContext<JakartaProducerStepSpecificationImpl<*>>) {
        val spec = creationContext.stepSpecification
        val stepId = spec.name
        val producer = JakartaProducer(
            stepName = stepId,
            connectionFactory = spec.connectionFactory,
            sessionFactory = spec.sessionFactory, converter = JakartaProducerConverter(),
            producersCount = spec.producersCount,
            eventsLogger = eventsLogger.takeIf { spec.monitoringConfig.events },
            meterRegistry = meterRegistry.takeIf { spec.monitoringConfig.meters }
        )

        val step = JakartaProducerStep(
            stepId = stepId,
            retryPolicy = spec.retryPolicy,
            recordFactory = spec.recordsFactory,
            jakartaProducer = producer,
        )
        creationContext.createdStep(step)
    }
}
