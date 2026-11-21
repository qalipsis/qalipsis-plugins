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

package io.qalipsis.plugins.http.connectionProvider.impl

import io.aerisconsulting.catadioptre.KTestable
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.context.StepStartStopContext
import io.qalipsis.plugins.http.HttpClientConfiguration
import io.qalipsis.plugins.http.connectionProvider.ConnectionProvider
import io.qalipsis.plugins.http.connectionProvider.HttpAsyncClientFactory
import java.util.concurrent.atomic.AtomicBoolean
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient
import org.apache.hc.core5.io.CloseMode

/**
 * Connection provider opening a new HTTP connection for each request, closed once the response is received.
 *
 * A single client - and its I/O threads - is shared by all the minions using the provider: only the connections
 * are created per request, so that the cost of a request remains the one of a TCP (and TLS) handshake.
 *
 * @author Francisca Eze
 */
class OnDemandConnectionProvider(private val httpClientConfiguration: HttpClientConfiguration) : ConnectionProvider {

    private val initialized = AtomicBoolean(false)

    @KTestable
    private lateinit var client: CloseableHttpAsyncClient

    override fun init(context: StepStartStopContext) {
        if (initialized.compareAndSet(false, true)) {
            client = HttpAsyncClientFactory.createOnDemandClient(httpClientConfiguration)
        }
    }

    override fun acquire(context: StepContext<*, *>): CloseableHttpAsyncClient = client

    override fun shutdown(context: StepStartStopContext) {
        if (initialized.compareAndSet(true, false)) {
            client.close(CloseMode.GRACEFUL)
        }
    }
}
