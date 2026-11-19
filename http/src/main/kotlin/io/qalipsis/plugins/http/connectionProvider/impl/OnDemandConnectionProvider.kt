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

import io.qalipsis.api.context.StepContext
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.plugins.http.HttpClientConfiguration
import io.qalipsis.plugins.http.connectionProvider.ConnectionProvider
import io.qalipsis.plugins.http.connectionProvider.HttpAsyncClientFactory
import org.apache.hc.client5.http.async.HttpAsyncClient
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient

/**
 * Connection provider that creates a new HTTP connection on demand for each minion.
 *
 * @author Francisca Eze
 */
class OnDemandConnectionProvider(private val httpClientConfiguration: HttpClientConfiguration) : ConnectionProvider {

    override fun acquire(context: StepContext<*, *>): CloseableHttpAsyncClient {
        log.info { "Acquiring a new client for the minion ${context.minionId}" }
        return HttpAsyncClientFactory.createOnDemandClient(httpClientConfiguration)
    }

    override fun release(context: StepContext<*, *>, client: HttpAsyncClient) {
        log.info { "Releasing the client for the minion ${context.minionId}" }
        (client as CloseableHttpAsyncClient).close()
    }

    companion object {

        @JvmStatic
        private val log = logger()
    }
}
