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

package io.qalipsis.plugins.http.request

import io.qalipsis.plugins.http.HttpClientConfiguration

/**
 * Base implementation of the [HttpRequest] interface, providing common logic for computing the complete URI.
 *
 * @author Francisca Eze
 */
abstract class AbstractHttpRequest<SELF : HttpRequest<SELF>> : HttpRequest<SELF> {

    private var fullUri: String? = null

    protected fun getCompleteUri(configuration: HttpClientConfiguration): String {
        return fullUri ?: doComputeUri(configuration)
    }

    private fun doComputeUri(configuration: HttpClientConfiguration): String {
        var processedUri = uri
        if (!processedUri.startsWith(configuration.scheme)) {
            val configurationHostAndPort = "${configuration.host}:${configuration.port}"
            if (!processedUri.startsWith(configurationHostAndPort)) {
                processedUri = if (processedUri.startsWith("/")) {
                    "${configurationHostAndPort}${configuration.contextPath}${processedUri}"
                } else {
                    "${configurationHostAndPort}${configuration.contextPath}/${processedUri}"
                }
            }
            processedUri = "${configuration.scheme}://${processedUri}"
        }
        fullUri = processedUri
        return processedUri
    }

}
