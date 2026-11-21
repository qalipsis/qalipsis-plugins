/*
 * QALIPSIS
 * Copyright (C) 2025 AERIS IT Solutions GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package io.qalipsis.plugins.http

import io.aerisconsulting.catadioptre.KTestable
import io.qalipsis.api.annotations.StepConverter
import io.qalipsis.api.events.EventsLogger
import io.qalipsis.api.meters.CampaignMeterRegistry
import io.qalipsis.api.steps.StepCreationContext
import io.qalipsis.api.steps.StepSpecification
import io.qalipsis.api.steps.StepSpecificationConverter
import io.qalipsis.plugins.http.connectionProvider.ConnectionProvider
import io.qalipsis.plugins.http.connectionProvider.impl.OnDemandConnectionProvider
import io.qalipsis.plugins.http.connectionProvider.impl.PoolConnectionProvider
import io.qalipsis.plugins.http.connectionProvider.impl.WarmupConnectionProvider
import io.qalipsis.plugins.http.response.HttpBodyDeserializer
import io.qalipsis.plugins.http.response.ResponseConverter
import java.util.concurrent.ConcurrentHashMap


@StepConverter
@Suppress("UNCHECKED_CAST")
internal class HttpClientStepSpecificationConverter(
    deserializers: Collection<HttpBodyDeserializer>,
    private val meterRegistry: CampaignMeterRegistry,
    private val eventsLogger: EventsLogger,
) : StepSpecificationConverter<HttpClientStepSpecificationImpl<*, *>> {

    private val sortedDeserializers = deserializers.sortedBy(HttpBodyDeserializer::order)

    @KTestable
    private val sharedProviders = ConcurrentHashMap<ConnectionStrategyConfiguration, ConnectionProvider>()

    override fun support(stepSpecification: StepSpecification<*, *, *>): Boolean {
        return (stepSpecification is HttpClientStepSpecificationImpl<*, *>)
    }

    override suspend fun <I, O> convert(creationContext: StepCreationContext<HttpClientStepSpecificationImpl<*, *>>) {
        val spec = creationContext.stepSpecification as HttpClientStepSpecificationImpl<I, O>
        val connectionStrategyConfiguration =
            spec.connectionConfiguration.connectionStrategyConfiguration
        val connectionProvider = resolveProvider(connectionStrategyConfiguration, spec.connectionConfiguration)
        val step = HttpClientStep<I, O>(
            id = spec.name,
            retryPolicy = spec.retryPolicy,
            connectionProvider = connectionProvider,
            requestFactory = spec.requestFactory,
            clientConfiguration = spec.connectionConfiguration,
            eventsLogger = eventsLogger.takeIf { spec.monitoringConfig.events },
            meterRegistry = meterRegistry.takeIf { spec.monitoringConfig.meters },
            responseConverter = ResponseConverter(spec.bodyType, sortedDeserializers),
        )
        creationContext.createdStep(step)
    }

    // Resolves the connection provider based on the connection strategy configuration.
    private fun resolveProvider(
        connectionStrategyConfiguration: ConnectionStrategyConfiguration,
        connectionConfiguration: HttpClientConfiguration
    ): ConnectionProvider {
        return if (connectionStrategyConfiguration.shared) {
            // Create or reuse already instantiated provider for shared connection strategies.
            sharedProviders.computeIfAbsent(connectionStrategyConfiguration) {
                createProvider(connectionStrategyConfiguration, connectionConfiguration)
            }
        } else {
            // Always create a fresh instance for atomic or non-shared connection strategies.
            createProvider(connectionStrategyConfiguration, connectionConfiguration)
        }
    }

    // Factory method to create the appropriate connection provider based on the strategy type.
    private fun createProvider(
        connectionStrategyConfiguration: ConnectionStrategyConfiguration,
        connectionConfiguration: HttpClientConfiguration
    ): ConnectionProvider {
        return when (connectionStrategyConfiguration.strategyType) {
            ConnectionStrategyType.ON_DEMAND -> OnDemandConnectionProvider(connectionConfiguration)
            ConnectionStrategyType.POOL -> PoolConnectionProvider(
                shared = connectionStrategyConfiguration.shared,
                httpClientConfiguration = connectionConfiguration
            )

            ConnectionStrategyType.WARMUP -> WarmupConnectionProvider(
                shared = connectionStrategyConfiguration.shared,
                httpClientConfiguration = connectionConfiguration
            )
        }
    }

}
