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

import org.apache.hc.client5.http.async.AsyncExecCallback
import org.apache.hc.client5.http.async.AsyncExecChain
import org.apache.hc.client5.http.async.AsyncExecChainHandler
import org.apache.hc.core5.http.HttpRequest
import org.apache.hc.core5.http.nio.AsyncEntityProducer

internal class MonitoringChainHandler : AsyncExecChainHandler {

    override fun execute(
        request: HttpRequest,
        entityProducer: AsyncEntityProducer?,
        scope: AsyncExecChain.Scope,
        chain: AsyncExecChain,
        asyncExecCallback: AsyncExecCallback,
    ) {
        chain.proceed(request, entityProducer, scope, asyncExecCallback)
    }
}