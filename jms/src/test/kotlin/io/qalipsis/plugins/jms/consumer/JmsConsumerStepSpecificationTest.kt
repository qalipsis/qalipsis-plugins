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

package io.qalipsis.plugins.jms.consumer

import assertk.all
import assertk.assertThat
import assertk.assertions.*
import io.aerisconsulting.catadioptre.getProperty
import io.qalipsis.api.scenario.StepSpecificationRegistry
import io.qalipsis.api.scenario.TestScenarioFactory
import io.qalipsis.api.steps.SingletonConfiguration
import io.qalipsis.api.steps.SingletonType
import io.qalipsis.plugins.jms.jms
import org.apache.activemq.ActiveMQConnectionFactory
import org.junit.jupiter.api.Test
import java.time.Duration

/**
 *
 * @author Alexander Sosnovsky
 */
internal class JmsConsumerStepSpecificationTest {

    private val queueConnection = ActiveMQConnectionFactory().createQueueConnection()

    private val topicConnection = ActiveMQConnectionFactory().createTopicConnection()

    @Test
    internal fun `should apply queue connection`() {
        val scenario = TestScenarioFactory.scenario("my-scenario") as StepSpecificationRegistry
        scenario.jms().consume {
            queueConnection { queueConnection }
            queues("queue-1", "queue-2")
        }

        assertThat(scenario.rootSteps[0]).isInstanceOf(JmsConsumerStepSpecification::class).all {
            prop(JmsConsumerStepSpecification<*>::configuration).all {
                prop(JmsConsumerConfiguration::queues).hasSize(2)
                prop(JmsConsumerConfiguration::topics).hasSize(0)
                prop(JmsConsumerConfiguration::topicConnectionFactory).isNull()
            }
            transform { it.metrics }.all {
                prop(JmsConsumerMetricsConfiguration::bytesCount).isFalse()
                prop(JmsConsumerMetricsConfiguration::recordsCount).isFalse()
            }
            transform { it.singletonConfiguration }.all {
                prop(SingletonConfiguration::type).isEqualTo(SingletonType.UNICAST)
                prop(SingletonConfiguration::bufferSize).isEqualTo(-1)
                prop(SingletonConfiguration::idleTimeout).isEqualTo(Duration.ZERO)
            }
        }

        val step = scenario.rootSteps[0] as JmsConsumerStepSpecification<*>
        val queueConnectionFactory = step.configuration.getProperty<() -> String>("queueConnectionFactory")
        assertThat(queueConnectionFactory.invoke()).isEqualTo(queueConnection)
    }

    @Test
    internal fun `should apply topic connection`() {
        val scenario = TestScenarioFactory.scenario("my-scenario") as StepSpecificationRegistry
        scenario.jms().consume {
            topicConnection { topicConnection }
            topics("topic-1", "topic-2")
        }

        assertThat(scenario.rootSteps[0]).isInstanceOf(JmsConsumerStepSpecification::class).all {
            prop(JmsConsumerStepSpecification<*>::configuration).all {
                prop(JmsConsumerConfiguration::queues).hasSize(0)
                prop(JmsConsumerConfiguration::topics).hasSize(2)
                prop(JmsConsumerConfiguration::queueConnectionFactory).isNull()
            }
            transform { it.metrics }.all {
                prop(JmsConsumerMetricsConfiguration::bytesCount).isFalse()
                prop(JmsConsumerMetricsConfiguration::recordsCount).isFalse()
            }
            transform { it.singletonConfiguration }.all {
                prop(SingletonConfiguration::type).isEqualTo(SingletonType.UNICAST)
                prop(SingletonConfiguration::bufferSize).isEqualTo(-1)
                prop(SingletonConfiguration::idleTimeout).isEqualTo(Duration.ZERO)
            }
        }

        val step = scenario.rootSteps[0] as JmsConsumerStepSpecification<*>

        val topicConnectionFactory = step.configuration.getProperty<() -> String>("topicConnectionFactory")
        assertThat(topicConnectionFactory.invoke()).isEqualTo(topicConnection)
    }

    @Test
    internal fun `should apply bytes count`() {
        val scenario = TestScenarioFactory.scenario("my-scenario") as StepSpecificationRegistry
        scenario.jms().consume {
            queueConnection { queueConnection }
            queues("queue-1", "queue-2")

            metrics {
                bytesCount = true
            }
        }

        assertThat(scenario.rootSteps[0]).isInstanceOf(JmsConsumerStepSpecification::class).all {
            transform { it.metrics }.all {
                prop(JmsConsumerMetricsConfiguration::bytesCount).isTrue()
                prop(JmsConsumerMetricsConfiguration::recordsCount).isFalse()
            }
        }
    }

    @Test
    internal fun `should apply records count`() {
        val scenario = TestScenarioFactory.scenario("my-scenario") as StepSpecificationRegistry
        scenario.jms().consume {
            queueConnection { queueConnection }
            queues("queue-1", "queue-2")

            metrics {
                recordsCount = true
            }
        }

        assertThat(scenario.rootSteps[0]).isInstanceOf(JmsConsumerStepSpecification::class).all {
            transform { it.metrics }.all {
                prop(JmsConsumerMetricsConfiguration::bytesCount).isFalse()
                prop(JmsConsumerMetricsConfiguration::recordsCount).isTrue()
            }
        }
    }

}
