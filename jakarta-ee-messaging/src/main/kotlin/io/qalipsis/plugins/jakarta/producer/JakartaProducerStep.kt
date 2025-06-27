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
 * @author Krawist Ngoben
 */
internal class JakartaProducerStep<I>(
    stepId: StepName,
    retryPolicy: RetryPolicy?,
    private val jakartaProducer: JakartaProducer,
    private val recordFactory: (suspend (ctx: StepContext<*, *>, input: I) -> List<JakartaProducerRecord>),
) : AbstractStep<I, JakartaProducerResult<I>>(stepId, retryPolicy) {

    override suspend fun start(context: StepStartStopContext) {
        jakartaProducer.start(context)
    }

    override suspend fun execute(context: StepContext<I, JakartaProducerResult<I>>) {
        val input = context.receive()
        val messages = recordFactory(context, input)

        val jakartaProducerMeters = jakartaProducer.execute(messages, context.toEventTags())

        context.send(JakartaProducerResult(input, jakartaProducerMeters))
    }

    override suspend fun stop(context: StepStartStopContext) {
        jakartaProducer.stop()
    }

}
