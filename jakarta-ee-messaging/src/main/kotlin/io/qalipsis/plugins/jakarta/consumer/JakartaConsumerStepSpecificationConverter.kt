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

package io.qalipsis.plugins.jakarta.consumer

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
import io.qalipsis.plugins.jakarta.JakartaDeserializer
import jakarta.jms.Message

/**
 * [StepSpecificationConverter] from [JakartaConsumerStepSpecification] to [JakartaConsumerIterativeReader] for a data source.
 *
 * @author Krawist Ngoben
 */
@StepConverter
internal class JakartaConsumerStepSpecificationConverter(
    private val meterRegistry: CampaignMeterRegistry,
    private val eventsLogger: EventsLogger
) : StepSpecificationConverter<JakartaConsumerStepSpecification<*>> {

    override fun support(stepSpecification: StepSpecification<*, *, *>): Boolean {
        return stepSpecification is JakartaConsumerStepSpecification<*>
    }

    override suspend fun <I, O> convert(creationContext: StepCreationContext<JakartaConsumerStepSpecification<*>>) {
        val spec = creationContext.stepSpecification
        val configuration = spec.configuration

        val stepId = spec.name
        val reader = JakartaConsumerIterativeReader(
            stepId,
            configuration.topics,
            configuration.queues,
            configuration.topicConnectionFactory,
            configuration.queueConnectionFactory,
            configuration.sessionFactory,
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
        valueDeserializer: JakartaDeserializer<*>,
        monitoringConfiguration: StepMonitoringConfiguration
    ): DatasourceObjectConverter<Message, out Any?> {
        return JakartaConsumerConverter(
            valueDeserializer,
            eventsLogger = eventsLogger.takeIf { monitoringConfiguration.events },
            meterRegistry = meterRegistry.takeIf { monitoringConfiguration.meters }
        )
    }

}
