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

package io.qalipsis.plugins.http.connectionProvider

import io.qalipsis.api.context.StepContext
import io.qalipsis.api.context.StepStartStopContext
import org.apache.hc.client5.http.async.HttpAsyncClient
import org.apache.hc.client5.http.protocol.HttpClientContext

/**
 * Connection provider shared by several steps: [delegate] is initialized when the first step starts and shut down
 * when the last one stops, so that a step stopping first does not close the connections of the others.
 *
 * @author Eric Jessé
 */
internal class SharedConnectionProvider(
    private val delegate: ConnectionProvider,
) : ConnectionProvider {

    private var usages = 0

    private val lock = Any()

    override fun init(context: StepStartStopContext) {
        synchronized(lock) {
            if (usages == 0) {
                delegate.init(context)
            }
            usages++
        }
    }

    override fun acquire(context: StepContext<*, *>): HttpAsyncClient = delegate.acquire(context)

    override fun prepare(context: StepContext<*, *>, httpContext: HttpClientContext) =
        delegate.prepare(context, httpContext)

    override fun release(context: StepContext<*, *>, client: HttpAsyncClient) = delegate.release(context, client)

    override fun shutdown(context: StepStartStopContext) {
        synchronized(lock) {
            if (usages > 0 && --usages == 0) {
                delegate.shutdown(context)
            }
        }
    }
}
