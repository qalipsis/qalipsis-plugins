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

package io.qalipsis.plugins.jms.producer

import io.qalipsis.api.context.StepContext
import io.qalipsis.api.context.StepName
import io.qalipsis.api.context.StepStartStopContext
import io.qalipsis.api.retry.RetryPolicy
import io.qalipsis.api.steps.AbstractStep


/**
 * Implementation of a [io.qalipsis.api.steps.Step] able to produce native JMS [Message]s to a JMS server.
 *
 * @property jmsProducer producer to use to execute the producing for the current step
 * @property recordFactory closure to generate list of [JmsProducerRecord]
 *
 * @author Alexander Sosnovsky
 */
internal class JmsProducerStep<I>(
    stepId: StepName,
    retryPolicy: RetryPolicy?,
    private val jmsProducer: JmsProducer,
    private val recordFactory: (suspend (ctx: StepContext<*, *>, input: I) -> List<JmsProducerRecord>),
) : AbstractStep<I, JmsProducerResult<I>>(stepId, retryPolicy) {

    override suspend fun start(context: StepStartStopContext) {
        jmsProducer.start(context)
    }

    override suspend fun execute(context: StepContext<I, JmsProducerResult<I>>) {
        val input = context.receive()
        val messages = recordFactory(context, input)

        val jmsProducerMeters = jmsProducer.execute(messages, context.toEventTags())

        context.send(JmsProducerResult(input, jmsProducerMeters))
    }

    override suspend fun stop(context: StepStartStopContext) {
        jmsProducer.stop()
    }

}
