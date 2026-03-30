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

package io.qalipsis.plugins.http

import io.aerisconsulting.catadioptre.KTestable
import io.qalipsis.api.Executors
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
import jakarta.inject.Named
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.CoroutineContext


@StepConverter
@Suppress("UNCHECKED_CAST")
internal class HttpClientStepSpecificationConverter(
    deserializers: Collection<HttpBodyDeserializer>,
    private val meterRegistry: CampaignMeterRegistry,
    private val eventsLogger: EventsLogger,
    @Named(Executors.IO_EXECUTOR_NAME) private val ioCoroutineContext: CoroutineContext
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
            ioCoroutineContext = ioCoroutineContext,
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
