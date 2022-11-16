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

package io.qalipsis.plugins.jakarta.producer

import io.qalipsis.api.context.StepContext
import io.qalipsis.api.context.StepName
import io.qalipsis.api.context.StepStartStopContext
import io.qalipsis.api.retry.RetryPolicy
import io.qalipsis.api.steps.AbstractStep


/**
 * Implementation of a [io.qalipsis.api.steps.Step] able to produce native JMS [jakarta.jms.Message]s to a JMS server.
 *
 * @property jakartaProducer producer to use to execute the producing for the current step
 * @property recordFactory closure to generate list of [JakartaProducerRecord]
 *
 * @author Alexander Sosnovsky
 */
internal class JakartaProducerStep<I>(
    stepId: StepName,
    retryPolicy: RetryPolicy?,
    private val jakartaProducer: JakartaProducer,
    private val recordFactory: (suspend (ctx: StepContext<*, *>, input: I) -> List<JakartaProducerRecord>),
) : AbstractStep<I, JakartaProducerResult<I>>(stepId, retryPolicy) {

    override suspend fun start(context: StepStartStopContext) {
        jakartaProducer.start(context.toMetersTags())
    }

    override suspend fun execute(context: StepContext<I, JakartaProducerResult<I>>) {
        val input = context.receive()
        val messages = recordFactory(context, input)

        val jmsProducerMeters = jakartaProducer.execute(messages, context.toEventTags())

        context.send(JakartaProducerResult(input, jmsProducerMeters))
    }

    override suspend fun stop(context: StepStartStopContext) {
        jakartaProducer.stop()
    }

}
