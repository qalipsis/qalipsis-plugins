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
