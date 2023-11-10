/*
 * Copyright 2023 AERIS IT Solutions GmbH
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

import assertk.all
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isSameAs
import assertk.assertions.prop
import io.mockk.mockk
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.steps.DummyStepSpecification
import io.qalipsis.plugins.jakarta.destination.Queue
import io.qalipsis.plugins.jakarta.destination.Topic
import io.qalipsis.plugins.jakarta.jakarta
import jakarta.jms.Connection
import jakarta.jms.Session
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.jupiter.api.Test

/**
 * @author Eric Jessé
 *
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class JakartaProducerStepSpecificationTest {

    @Test
    internal fun `should configure the step`() {
        val rec1 = JakartaProducerRecord(
            destination = Topic("topic-1"),
            value = "text-1"
        )
        val rec2 = JakartaProducerRecord(
            destination = Queue("queue-1"),
            value = "text-2"
        )

        val recordSupplier: (suspend (ctx: StepContext<*, *>, input: Any?) -> List<JakartaProducerRecord>) =
            { _, _ -> listOf(rec1, rec2) }

        val connectionFactory: (() -> Connection) = { mockk() }
        val sessionFactory: ((Connection) -> Session) = { mockk() }
        val previousStep = DummyStepSpecification()
        previousStep.jakarta().produce {
            name = "my-producer-step"
            connect(connectionFactory)
            session(sessionFactory)
            records(recordSupplier)
            producers(2)
        }

        assertThat(previousStep.nextSteps[0]).isInstanceOf(JakartaProducerStepSpecificationImpl::class).all {
            prop(JakartaProducerStepSpecificationImpl<*>::producersCount).isEqualTo(2)
            prop(JakartaProducerStepSpecificationImpl<*>::recordsFactory).isSameAs(recordSupplier)
            prop(JakartaProducerStepSpecificationImpl<*>::connectionFactory).isSameAs(connectionFactory)
            prop(JakartaProducerStepSpecificationImpl<*>::sessionFactory).isSameAs(sessionFactory)
        }
    }


}
