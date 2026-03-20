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

package io.qalipsis.plugins.netty.http

import io.qalipsis.api.Executors
import io.qalipsis.api.annotations.StepConverter
import io.qalipsis.api.events.EventsLogger
import io.qalipsis.api.meters.CampaignMeterRegistry
import io.qalipsis.api.steps.StepCreationContext
import io.qalipsis.api.steps.StepSpecification
import io.qalipsis.api.steps.StepSpecificationConverter
import io.qalipsis.plugins.netty.EventLoopGroupSupplier
import io.qalipsis.plugins.netty.http.response.HttpBodyDeserializer
import io.qalipsis.plugins.netty.http.response.ResponseConverter
import io.qalipsis.plugins.netty.http.spec.HttpClientStepSpecificationImpl
import io.qalipsis.plugins.netty.socket.ConnectionStrategyType
import jakarta.inject.Named
import kotlin.coroutines.CoroutineContext

/**
 * [StepSpecificationConverter] from [HttpClientStepSpecificationImpl] to [SimpleHttpClientStep].
 *
 * @author Eric Jessé
 */
@StepConverter
@Suppress("UNCHECKED_CAST")
internal class HttpClientStepSpecificationConverter(
    deserializers: Collection<HttpBodyDeserializer>,
    private val eventLoopGroupSupplier: EventLoopGroupSupplier,
    private val eventsLogger: EventsLogger,
    private val meterRegistry: CampaignMeterRegistry,
    @Named(Executors.IO_EXECUTOR_NAME) private val ioCoroutineContext: CoroutineContext
) : StepSpecificationConverter<HttpClientStepSpecificationImpl<*, *>> {

    private val sortedDeserializers = deserializers.sortedBy(HttpBodyDeserializer::order)

    override fun support(stepSpecification: StepSpecification<*, *, *>): Boolean {
        return (stepSpecification is HttpClientStepSpecificationImpl<*, *>)
    }

    override suspend fun <I, O> convert(creationContext: StepCreationContext<HttpClientStepSpecificationImpl<*, *>>) {
        val spec = creationContext.stepSpecification as HttpClientStepSpecificationImpl<I, O>
        val responseConverter = ResponseConverter<O>(spec.bodyType, sortedDeserializers)
        val effectiveEventsLogger = eventsLogger.takeIf { spec.monitoringConfiguration.events }
        val effectiveMeterRegistry = meterRegistry.takeIf { spec.monitoringConfiguration.meters }

        val step = when (spec.connectionConfiguration.connectionStrategyConfiguration.strategyType) {
            ConnectionStrategyType.POOL -> {
                val poolConfig = spec.connectionConfiguration.poolConfiguration
                    ?: io.qalipsis.plugins.netty.tcp.spec.SocketClientPoolConfiguration()
                spec.connectionConfiguration.keepConnectionAlive = true
                PooledHttpClientStep(
                    spec.name,
                    spec.retryPolicy,
                    ioCoroutineContext,
                    spec.requestFactory,
                    spec.connectionConfiguration,
                    poolConfig,
                    eventLoopGroupSupplier,
                    responseConverter,
                    effectiveEventsLogger,
                    effectiveMeterRegistry
                )
            }

            ConnectionStrategyType.WARMUP -> {
                spec.connectionConfiguration.keepConnectionAlive = true
                WarmupHttpClientStep<I, O>(
                    spec.name,
                    spec.retryPolicy,
                    spec.requestFactory,
                    spec.connectionConfiguration,
                    spec.connectionConfiguration.connectionStrategyConfiguration.shared,
                    eventLoopGroupSupplier,
                    responseConverter,
                    effectiveEventsLogger,
                    effectiveMeterRegistry
                )
            }

            ConnectionStrategyType.ON_DEMAND -> {
                if (spec.connectionConfiguration.poolConfiguration != null) {
                    // Backward compatibility: if pool() was called without connectionStrategy(), use pooled step.
                    spec.connectionConfiguration.keepConnectionAlive = true
                    PooledHttpClientStep(
                        spec.name,
                        spec.retryPolicy,
                        ioCoroutineContext,
                        spec.requestFactory,
                        spec.connectionConfiguration,
                        spec.connectionConfiguration.poolConfiguration!!,
                        eventLoopGroupSupplier,
                        responseConverter,
                        effectiveEventsLogger,
                        effectiveMeterRegistry
                    )
                } else {
                    SimpleHttpClientStep<I, O>(
                        spec.name,
                        spec.retryPolicy,
                        spec.requestFactory,
                        spec.connectionConfiguration,
                        eventLoopGroupSupplier,
                        responseConverter,
                        effectiveEventsLogger,
                        effectiveMeterRegistry
                    )
                }
            }
        }

        creationContext.createdStep(step)
    }

}
