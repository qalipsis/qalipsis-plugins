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

package io.qalipsis.plugins.jakarta.producer

import io.micrometer.core.instrument.MeterRegistry
import io.qalipsis.api.annotations.StepConverter
import io.qalipsis.api.events.EventsLogger
import io.qalipsis.api.steps.StepCreationContext
import io.qalipsis.api.steps.StepSpecification
import io.qalipsis.api.steps.StepSpecificationConverter
import kotlinx.coroutines.ExperimentalCoroutinesApi

/**
 * [StepSpecificationConverter] from [JakartaProducerStepSpecificationImpl] to [JakartaProducerStep]
 * to use the Produce API.
 *
 * @author Alexander Sosnovsky
 */
@ExperimentalCoroutinesApi
@StepConverter
internal class JakartaProducerStepSpecificationConverter(
    private val meterRegistry: MeterRegistry,
    private val eventsLogger: EventsLogger
) : StepSpecificationConverter<JakartaProducerStepSpecificationImpl<*>> {

    override fun support(stepSpecification: StepSpecification<*, *, *>): Boolean {
        return stepSpecification is JakartaProducerStepSpecificationImpl
    }

    override suspend fun <I, O> convert(creationContext: StepCreationContext<JakartaProducerStepSpecificationImpl<*>>) {
        val spec = creationContext.stepSpecification
        val stepId = spec.name
        val producer = JakartaProducer(spec.connectionFactory, JakartaProducerConverter(),
            eventsLogger = eventsLogger.takeIf { spec.monitoringConfig.events },
            meterRegistry = meterRegistry.takeIf { spec.monitoringConfig.meters }
        )

        @Suppress("UNCHECKED_CAST")
        val step = JakartaProducerStep(
            stepId = stepId,
            retryPolicy = spec.retryPolicy,
            recordFactory = spec.recordsFactory,
            jakartaProducer = producer,
        )
        creationContext.createdStep(step)
    }
}
