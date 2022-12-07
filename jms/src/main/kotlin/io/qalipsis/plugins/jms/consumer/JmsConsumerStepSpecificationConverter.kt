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

package io.qalipsis.plugins.jms.consumer

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
import io.qalipsis.plugins.jms.JmsDeserializer
import javax.jms.Message

/**
 * [StepSpecificationConverter] from [JmsConsumerStepSpecification] to [JmsConsumerIterativeReader] for a data source.
 *
 * @author Alexander Sosnovsky
 */
@StepConverter
internal class JmsConsumerStepSpecificationConverter(
    private val meterRegistry: CampaignMeterRegistry,
    private val eventsLogger: EventsLogger
) : StepSpecificationConverter<JmsConsumerStepSpecification<*>> {

    override fun support(stepSpecification: StepSpecification<*, *, *>): Boolean {
        return stepSpecification is JmsConsumerStepSpecification<*>
    }

    override suspend fun <I, O> convert(creationContext: StepCreationContext<JmsConsumerStepSpecification<*>>) {
        val spec = creationContext.stepSpecification
        val configuration = spec.configuration

        val stepId = spec.name
        val reader = JmsConsumerIterativeReader(
            stepId,
            configuration.topics,
            configuration.queues,
            configuration.topicConnectionFactory,
            configuration.queueConnectionFactory,
        )

        val step = IterativeDatasourceStep(
            stepId,
            reader,
            NoopDatasourceObjectProcessor(),
            buildConverter(spec.valueDeserializer, spec.monitoringConfig)
        )
        creationContext.createdStep(step)
    }

    private fun buildConverter(
        valueDeserializer: JmsDeserializer<*>,
        monitoringConfiguration: StepMonitoringConfiguration
    ): DatasourceObjectConverter<Message, out Any?> {
        return JmsConsumerConverter(
            valueDeserializer,
            eventsLogger = eventsLogger.takeIf { monitoringConfiguration.events },
            meterRegistry = meterRegistry.takeIf { monitoringConfiguration.meters }
        )
    }

}
