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

package io.qalipsis.plugins.rabbitmq.producer

import com.rabbitmq.client.ConnectionFactory
import io.aerisconsulting.catadioptre.KTestable
import io.qalipsis.api.annotations.StepConverter
import io.qalipsis.api.events.EventsLogger
import io.qalipsis.api.meters.CampaignMeterRegistry
import io.qalipsis.api.steps.StepCreationContext
import io.qalipsis.api.steps.StepSpecification
import io.qalipsis.api.steps.StepSpecificationConverter
import io.qalipsis.plugins.rabbitmq.configuration.RabbitMqConnectionConfiguration
import kotlinx.coroutines.ExperimentalCoroutinesApi
import java.time.Duration

/**
 * [StepSpecificationConverter] from [RabbitMqProducerStepSpecificationImpl] to [RabbitMqProducerStep]
 * to use the Produce API.
 *
 * @author Alexander Sosnovsky
 */
@ExperimentalCoroutinesApi
@StepConverter
internal class RabbitMqProducerStepSpecificationConverter(
    private val meterRegistry: CampaignMeterRegistry,
    private val eventsLogger: EventsLogger
) : StepSpecificationConverter<RabbitMqProducerStepSpecificationImpl<*>> {

    override fun support(stepSpecification: StepSpecification<*, *, *>): Boolean {
        return stepSpecification is RabbitMqProducerStepSpecificationImpl
    }

    override suspend fun <I, O> convert(creationContext: StepCreationContext<RabbitMqProducerStepSpecificationImpl<*>>) {
        val spec = creationContext.stepSpecification
        val stepId = spec.name
        val producer = RabbitMqProducer(4, buildConnectionFactory(spec.connectionConfiguration))

        @Suppress("UNCHECKED_CAST")
        val step = RabbitMqProducerStep(
            stepName = stepId,
            retryPolicy = spec.retryPolicy,
            recordFactory = spec.recordsFactory,
            rabbitMqProducer = producer,
            eventsLogger = eventsLogger.takeIf { spec.monitoring.events },
            meterRegistry = meterRegistry.takeIf { spec.monitoring.meters }
        )
        creationContext.createdStep(step)
    }

    @KTestable
    private fun buildConnectionFactory(connectionConfiguration: RabbitMqConnectionConfiguration): ConnectionFactory {
        val connectionFactory = ConnectionFactory()
        connectionFactory.host = connectionConfiguration.host
        connectionFactory.port = connectionConfiguration.port
        connectionFactory.username = connectionConfiguration.username
        connectionFactory.password = connectionConfiguration.password

        connectionFactory.virtualHost = connectionConfiguration.virtualHost
        connectionFactory.clientProperties = connectionConfiguration.clientProperties
        connectionFactory.connectionTimeout = DEFAULT_CONNECTION_TIMEOUT
        connectionFactory.useNio()

        return connectionFactory
    }

    companion object {

        /**
         * Timeout used to connect to the RabbitMQ broker.
         */
        @JvmStatic
        private val DEFAULT_CONNECTION_TIMEOUT = Duration.ofSeconds(30).toMillis().toInt()
    }

}
