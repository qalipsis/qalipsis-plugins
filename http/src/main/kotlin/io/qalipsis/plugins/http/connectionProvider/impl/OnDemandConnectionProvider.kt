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
