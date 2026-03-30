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

package io.qalipsis.plugins.graphite.search

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
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