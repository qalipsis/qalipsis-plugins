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

package io.qalipsis.plugins.graphite

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.qalipsis.plugins.graphite.search.DataPoints
import org.awaitility.kotlin.await
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.http.HttpTimeoutException
import java.time.Duration

private val SERIES_CREATION_TIMEOUT = Duration.ofSeconds(30)

private val RENDER_REQUEST_TIMEOUT = Duration.ofSeconds(5)

/**
 * Renders the series whose name starts with [keyPrefix], tagged or not, until at least [expectedCount] are returned:
 * Graphite creates the series asynchronously, and not all at once.
 *
 * Each request and the whole wait are limited in time, so that an unresponsive Graphite fails the test. A request
 * reaching its timeout is retried as long as the wait goes on.
 */
internal fun HttpClient.awaitRenderedSeries(
    httpPort: Int,
    keyPrefix: String,
    expectedCount: Int,
    objectMapper: ObjectMapper
): List<DataPoints> {
    val request = HttpRequest.newBuilder()
        .GET()
        .timeout(RENDER_REQUEST_TIMEOUT)
        .uri(URI.create("http://localhost:$httpPort/render?target=$keyPrefix.**&target=seriesByTag('name=~$keyPrefix.*')&noNullPoints=True&format=json"))
        .build()
    var dataPoints = emptyList<DataPoints>()
    await.atMost(SERIES_CREATION_TIMEOUT).ignoreException(HttpTimeoutException::class.java).until {
        dataPoints = objectMapper.readValue(send(request, HttpResponse.BodyHandlers.ofString()).body())
        dataPoints.size >= expectedCount
    }
    return dataPoints
}
