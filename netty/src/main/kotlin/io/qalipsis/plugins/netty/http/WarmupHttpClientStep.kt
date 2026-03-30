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

    override fun createMonitoringCollector(context: StepContext<I, ConnectionAndRequestResult<I, QalipsisHttpResponse<O>>>): StepContextBasedSocketMonitoringCollector {
        return HttpStepContextBasedSocketMonitoringCollector(context, eventsLogger, meterRegistry)
    }

    override fun convertResponseToOutput(response: HttpResponse): QalipsisHttpResponse<O> {
        return responseConverter.convert(response)
    }
}
