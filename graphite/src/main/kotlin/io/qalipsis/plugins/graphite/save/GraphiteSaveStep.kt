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

import io.qalipsis.api.context.StepContext
import io.qalipsis.api.context.StepName
import io.qalipsis.api.context.StepStartStopContext
import io.qalipsis.api.retry.RetryPolicy
import io.qalipsis.api.steps.AbstractStep

/**
 * Implementation of a [io.qalipsis.api.steps.Step] able to perform inserts into Graphite.
 *
 * @property GraphiteSaveMessageClient client to use to execute the io.qalipsis.plugins.graphite.save for the current step.
 * @property messageFactory closure to generate a list of messages.
 *
 * @author Palina Bril
 */
internal class GraphiteSaveStep<I>(
    id: StepName,
    retryPolicy: RetryPolicy?,
    private val graphiteSaveMessageClient: GraphiteSaveMessageClient,
    private val messageFactory: (suspend (ctx: StepContext<*, *>, input: I) -> List<GraphiteRecord>)
) : AbstractStep<I, GraphiteSaveResult<I>>(id, retryPolicy) {

    override suspend fun start(context: StepStartStopContext) {
        graphiteSaveMessageClient.start(context)
    }

    override suspend fun execute(context: StepContext<I, GraphiteSaveResult<I>>) {
        val input = context.receive()
        val messages = messageFactory(context, input)

        val metrics = graphiteSaveMessageClient.execute(messages, context.toEventTags())

        context.send(GraphiteSaveResult(input, messages, metrics))
    }

    override suspend fun stop(context: StepStartStopContext) {
        graphiteSaveMessageClient.stop(context)
    }
}
