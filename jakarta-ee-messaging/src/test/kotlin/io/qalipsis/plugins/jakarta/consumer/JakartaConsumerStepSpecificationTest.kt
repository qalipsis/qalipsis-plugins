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

package io.qalipsis.plugins.jakarta.consumer

import assertk.all
import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNull
import assertk.assertions.isSameAs
import assertk.assertions.prop
import io.mockk.mockk
import io.qalipsis.api.scenario.StepSpecificationRegistry
import io.qalipsis.api.scenario.TestScenarioFactory
import io.qalipsis.api.steps.SingletonConfiguration
import io.qalipsis.api.steps.SingletonType
import io.qalipsis.plugins.jakarta.jakarta
import jakarta.jms.Connection
import jakarta.jms.QueueConnection
import jakarta.jms.Session
import jakarta.jms.TopicConnection
import org.junit.jupiter.api.Test
import java.time.Duration

/**
 * @author Krawist Ngoben
 */
internal class JakartaConsumerStepSpecificationTest {

    @Test
    internal fun `should apply queue connection`() {
        val scenario = TestScenarioFactory.scenario("my-scenario") as StepSpecificationRegistry
        val queueConnection: (() -> QueueConnection) = { mockk() }
        val sessionFactory: ((Connection) -> Session) = { mockk() }
        scenario.jakarta().consume {
            queueConnection(queueConnection)
            queues("queue-1", "queue-2")
            session(sessionFactory)
        }

        assertThat(scenario.rootSteps[0]).isInstanceOf(JakartaConsumerStepSpecification::class).all {
            prop(JakartaConsumerStepSpecification<*>::configuration).all {
                prop(JakartaConsumerConfiguration::queues).hasSize(2)
                prop(JakartaConsumerConfiguration::topics).hasSize(0)
                prop(JakartaConsumerConfiguration::topicConnectionFactory).isNull()
                prop(JakartaConsumerConfiguration::sessionFactory).isSameAs(sessionFactory)
            }
            transform { it.singletonConfiguration }.all {
                prop(SingletonConfiguration::type).isEqualTo(SingletonType.UNICAST)
                prop(SingletonConfiguration::bufferSize).isEqualTo(-1)
                prop(SingletonConfiguration::idleTimeout).isEqualTo(Duration.ZERO)
            }
        }
    }

    @Test
    internal fun `should apply topic connection`() {
        val scenario = TestScenarioFactory.scenario("my-scenario") as StepSpecificationRegistry
        val topicConnection: (() -> TopicConnection) = { mockk() }
        val sessionFactory: ((Connection) -> Session) = { mockk() }
        scenario.jakarta().consume {
            topicConnection(topicConnection)
            topics("topic-1", "topic-2")
            session(sessionFactory)
        }

        assertThat(scenario.rootSteps[0]).isInstanceOf(JakartaConsumerStepSpecification::class).all {
            prop(JakartaConsumerStepSpecification<*>::configuration).all {
                prop(JakartaConsumerConfiguration::queues).hasSize(0)
                prop(JakartaConsumerConfiguration::topics).hasSize(2)
                prop(JakartaConsumerConfiguration::queueConnectionFactory).isNull()
                prop(JakartaConsumerConfiguration::sessionFactory).isSameAs(sessionFactory)
            }
            transform { it.singletonConfiguration }.all {
                prop(SingletonConfiguration::type).isEqualTo(SingletonType.UNICAST)
                prop(SingletonConfiguration::bufferSize).isEqualTo(-1)
                prop(SingletonConfiguration::idleTimeout).isEqualTo(Duration.ZERO)
            }
        }
    }


}
