/*
 * Copyright 2024 AERIS IT Solutions GmbH
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

package io.qalipsis.plugins.graphite.search

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.statement.HttpResponse
import jakarta.inject.Singleton

/**
 * Graphite render api service to get response from server and map it as [GraphiteRenderApiJsonResponse].
 *
 * @param serverUrl of a server from where the response is received
 * @property objectMapper to map values from JSON to the list of [GraphiteRenderApiJsonResponse]
 * @property httpClient to perform a GET request
 * @property baseAuth parameters to authenticate when performing GET request
 *
 * @author rklymenko
 */
@Singleton
internal class GraphiteRenderApiService(
    serverUrl: String,
    private val objectMapper: ObjectMapper,
    private val httpClient: HttpClient,
    private val baseAuth: String? = null
) {

    private val rootServerUrl = if (serverUrl.endsWith('/')) {
        serverUrl.substringBeforeLast('/')
    } else {
        serverUrl
    }

    suspend fun execute(graphiteQuery: GraphiteQuery): List<DataPoints> {
        return try {
            val response = this.doExecute(graphiteQuery)
            objectMapper.readValue<List<DataPoints>>(response.body<ByteArray>())
        } catch (e: GraphiteHttpQueryException) {
            throw e
        } catch (e: Throwable) {
            throw GraphiteHttpQueryException(e)
        }
    }

    private suspend fun doExecute(graphiteQuery: GraphiteQuery): HttpResponse {
        val response = httpClient.get(rootServerUrl + graphiteQuery.build()) {
            headers {
                baseAuth?.let { append("Authorization", "Basic $it") }
            }
        }
        if (response.status.value >= 300) {
            throw GraphiteHttpQueryException("The HTTP query failed, HTTP status: ${response.status.value}, response: ${response.body<String>()}")
        }
        return response
    }

    fun close() {
        httpClient.close()
    }

}