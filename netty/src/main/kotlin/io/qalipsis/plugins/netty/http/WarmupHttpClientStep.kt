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

package io.qalipsis.plugins.netty.http

import io.qalipsis.api.context.StepStartStopContext
import io.qalipsis.plugins.netty.monitoring.StepMetersCache
import io.netty.channel.EventLoopGroup
import io.netty.handler.codec.http.HttpResponse
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.context.StepName
import io.qalipsis.api.events.EventsLogger
import io.qalipsis.api.meters.CampaignMeterRegistry
import io.qalipsis.api.retry.RetryPolicy
import io.qalipsis.plugins.netty.EventLoopGroupSupplier
import io.qalipsis.plugins.netty.http.client.MultiSocketHttpClient
import io.qalipsis.plugins.netty.http.client.monitoring.HttpStepContextBasedSocketMonitoringCollector
import io.qalipsis.plugins.netty.http.request.HttpRequest
import io.qalipsis.plugins.netty.http.response.ResponseConverter
import io.qalipsis.plugins.netty.http.spec.HttpClientConfiguration
import io.qalipsis.plugins.netty.monitoring.StepBasedTcpMonitoringCollector
import io.qalipsis.plugins.netty.monitoring.StepContextBasedSocketMonitoringCollector
import io.qalipsis.plugins.netty.socket.WarmupSocketClientStep
import io.qalipsis.plugins.netty.tcp.ConnectionAndRequestResult
import io.qalipsis.plugins.netty.http.response.HttpResponse as QalipsisHttpResponse

/**
 * Step to send and receive data using HTTP, using a warmup connection strategy where
 * all connections are pre-created at start and assigned to minions on demand.
 *
 * Each pre-warmed [MultiSocketHttpClient] supports automatic redirect handling across
 * different remote peers.
 *
 * @author Eric Jesse
 */
internal class WarmupHttpClientStep<I, O>(
    id: StepName,
    retryPolicy: RetryPolicy?,
    requestFactory: suspend HttpRequestBuilder.(StepContext<*, *>, I) -> HttpRequest<*>,
    private val clientConfiguration: HttpClientConfiguration,
    shared: Boolean,
    eventLoopGroupSupplier: EventLoopGroupSupplier,
    private val responseConverter: ResponseConverter<O>,
    private val eventsLogger: EventsLogger?,
    private val meterRegistry: CampaignMeterRegistry?,
) : WarmupSocketClientStep<I, QalipsisHttpResponse<O>, HttpClientConfiguration, HttpRequest<*>, HttpResponse, HttpRequestBuilder, MultiSocketHttpClient>(
    id,
    retryPolicy,
    HttpRequestBuilderImpl,
    requestFactory,
    clientConfiguration,
    shared,
    "http",
    eventLoopGroupSupplier,
    eventsLogger,
    meterRegistry
), HttpClientStep<I, ConnectionAndRequestResult<I, QalipsisHttpResponse<O>>> {

    override suspend fun createClient(
        workerGroup: EventLoopGroup,
        monitoringCollector: StepBasedTcpMonitoringCollector,
    ): MultiSocketHttpClient {
        val cli = MultiSocketHttpClient(Long.MAX_VALUE)
        cli.open(clientConfiguration, workerGroup, monitoringCollector)
        return cli
    }

    private val metersCache = StepMetersCache()

    override suspend fun start(context: StepStartStopContext) {
        metersCache.clear()
        super<WarmupSocketClientStep>.start(context)
    }

    override suspend fun stop(context: StepStartStopContext) {
        super<WarmupSocketClientStep>.stop(context)
        metersCache.clear()
    }

    override fun createMonitoringCollector(context: StepContext<I, ConnectionAndRequestResult<I, QalipsisHttpResponse<O>>>): StepContextBasedSocketMonitoringCollector {
        return HttpStepContextBasedSocketMonitoringCollector(context, eventsLogger, meterRegistry, metersCache)
    }

    override fun convertResponseToOutput(response: HttpResponse): QalipsisHttpResponse<O> {
        return responseConverter.convert(response)
    }
}
