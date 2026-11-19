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

package io.qalipsis.plugins.http.client

import org.apache.hc.core5.http.EntityDetails
import org.apache.hc.core5.http.HttpRequest
import org.apache.hc.core5.http.HttpRequestInterceptor
import org.apache.hc.core5.http.protocol.HttpContext

/**
 * Implementation of [RequestMonitoringInterceptor] in charge of completing the [HttpContext] with monitoring details.
 */
internal class RequestMonitoringInterceptor : HttpRequestInterceptor {

    override fun process(request: HttpRequest, entity: EntityDetails?, context: HttpContext) {
        context.setAttribute(SENT_BYTES_ATTR, entity?.contentLength ?: 0L)
        context.setAttribute(SENT_ATTR, System.nanoTime())
    }

    companion object {

        const val SENT_ATTR = "monitoring.sent"

        const val SENT_BYTES_ATTR = "monitoring.sent-bytes"
    }
}