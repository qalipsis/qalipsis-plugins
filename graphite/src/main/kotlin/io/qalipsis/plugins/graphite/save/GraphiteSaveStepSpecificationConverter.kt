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

package io.qalipsis.plugins.graphite.save

import io.qalipsis.api.annotations.StepConverter
import io.qalipsis.api.events.EventsLogger
import io.qalipsis.api.lang.supplyIf
import io.qalipsis.api.meters.CampaignMeterRegistry
import io.qalipsis.api.steps.StepCreationContext
import io.qalipsis.api.steps.StepSpecification
import io.qalipsis.api.steps.StepSpecificationConverter
import io.qalipsis.plugins.graphite.GraphiteProtocol
import io.qalipsis.plugins.graphite.client.GraphiteRecord
import io.qalipsis.plugins.graphite.client.GraphiteTcpClient
import io.qalipsis.plugins.graphite.client.codecs.PickleEncoder
import io.qalipsis.plugins.graphite.client.codecs.PlaintextEncoder

/**
 * [StepSpecificationConverter] from [GraphiteSaveStepSpecificationImpl] to [GraphiteSaveStep]
 * to use the Save API.
 *
 * @author Palina Bril
 */
@StepConverter
internal class GraphiteSaveStepSpecificationConverter(
    private val meterRegistry: CampaignMeterRegistry,
    private val eventsLogger: EventsLogger
) : StepSpecificationConverter<GraphiteSaveStepSpecificationImpl<*>> {

    override fun support(stepSpecification: StepSpecification<*, *, *>): Boolean {
        return stepSpecification is GraphiteSaveStepSpecificationImpl
    }

    override suspend fun <I, O> convert(creationContext: StepCreationContext<GraphiteSaveStepSpecificationImpl<*>>) {
        val spec = creationContext.stepSpecification
        val stepId = spec.name
        val clientBuilder = {
            val encoder = when (spec.connectionConfig.protocol) {
                GraphiteProtocol.PLAINTEXT -> PlaintextEncoder()
                GraphiteProtocol.PICKLE -> PickleEncoder()
            }
            GraphiteTcpClient<GraphiteRecord>(
                host = spec.connectionConfig.host,
                port = spec.connectionConfig.port,
                encoders = listOf(encoder),
                workerGroup = spec.connectionConfig.nettyWorkerGroup(),
                channelClass = spec.connectionConfig.nettyChannelClass
            )
        }

        val step = GraphiteSaveStep(
            id = stepId,
            retryPolicy = spec.retryPolicy,
            clientBuilder = clientBuilder,
            eventsLogger = supplyIf(spec.monitoringConfig.events) { eventsLogger },
            meterRegistry = supplyIf(spec.monitoringConfig.meters) { meterRegistry },
            messageFactory = spec.records
        )
        creationContext.createdStep(step)
    }
}
