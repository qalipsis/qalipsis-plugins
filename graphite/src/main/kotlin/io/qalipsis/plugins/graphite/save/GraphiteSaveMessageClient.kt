/*
 * Copyright 2022 AERIS IT Solutions GmbH
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

package io.qalipsis.plugins.graphite.save

import io.qalipsis.api.context.StepStartStopContext


/**
 * Client to save messages to Graphite.
 *
 * @author Palina Bril
 */
internal interface GraphiteSaveMessageClient {

    /**
     * Initializes the client and connects to the Graphite server.
     */
    suspend fun start(context: StepStartStopContext)

    /**
     * Inserts messages to the Graphite server.
     */
    suspend fun execute(
        messages: List<String>,
        contextEventTags: Map<String, String>
    ): GraphiteSaveQueryMeters

    /**
     * Cleans the client and closes the connections to the Graphite server.
     */
    suspend fun stop(context: StepStartStopContext)
}
