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

package io.qalipsis.plugins.graphite.render.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.qalipsis.plugins.graphite.render.model.GraphiteMetricsRequestBuilder
import io.qalipsis.plugins.graphite.render.model.GraphiteRenderApiJsonResponse
import io.qalipsis.plugins.graphite.render.model.GraphiteRenderFormat
import jakarta.inject.Singleton

/**
 * Graphite render api service to get response from server and map it as [GraphiteRenderApiJsonResponse].
 *
 * @property serverUrl of a server from where the response is received
 * @property objectMapper to map values from JSON to the list of [GraphiteRenderApiJsonResponse]
 * @property httpClient to perform a GET request
 * @property baseAuth parameters to authenticate when performing GET request
 *
 * @author rklymenko
 */
@Singleton
internal class GraphiteRenderApiService(
    private val serverUrl: String,
    private val objectMapper: ObjectMapper,
    private val httpClient: HttpClient,
    private val baseAuth: String? = null
) {

    suspend fun getAsJson(graphiteMetricsRequestBuilder: GraphiteMetricsRequestBuilder): List<GraphiteRenderApiJsonResponse> {
        graphiteMetricsRequestBuilder.format(GraphiteRenderFormat.JSON)
        val response = queryCustomFormatAsString(graphiteMetricsRequestBuilder)
        return objectMapper.readValue(response)
    }

    suspend fun queryCustomFormatAsString(graphiteMetricsRequestBuilder: GraphiteMetricsRequestBuilder): String {
        val requestUri = serverUrl + graphiteMetricsRequestBuilder.build()
        return httpClient.get(requestUri) {
            headers {
                baseAuth?.let { append("Authorization", "Basic $it") }
            }
        }.body()
    }

    fun close() {
        httpClient.close()
    }

}