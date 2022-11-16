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
import assertk.assertions.*
import io.aerisconsulting.catadioptre.getProperty
import io.qalipsis.api.scenario.StepSpecificationRegistry
import io.qalipsis.api.scenario.TestScenarioFactory
import io.qalipsis.api.steps.SingletonConfiguration
import io.qalipsis.api.steps.SingletonType
import io.qalipsis.plugins.jakarta.Constants
import io.qalipsis.plugins.jakarta.jakarta
import jakarta.jms.QueueConnection
import jakarta.jms.TopicConnection
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.testcontainers.containers.GenericContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Duration
import kotlin.math.pow

/**
 *
 * @author Alexander Sosnovsky
 */
@Testcontainers
internal class JakartaConsumerStepSpecificationIntegrationTest {

    private lateinit var queueConnection : QueueConnection

    private lateinit var topicConnection : TopicConnection

    @BeforeAll
    fun initGlobal(){
        val connectionFactory = ActiveMQConnectionFactory("tcp://localhost:${container.getMappedPort(61616)}", Constants.CONTAINER_USER_NAME, Constants.CONTAINER_PASSWORD)
        queueConnection = connectionFactory.createQueueConnection()
        topicConnection = connectionFactory.createTopicConnection()
    }

    @Test
    internal fun `should apply queue connection`() {
        val scenario = TestScenarioFactory.scenario("my-scenario") as StepSpecificationRegistry
        scenario.jakarta().consume {
            queueConnection { queueConnection }
            queues("queue-1", "queue-2")
        }

        assertThat(scenario.rootSteps[0]).isInstanceOf(JakartaConsumerStepSpecification::class).all {
            prop(JakartaConsumerStepSpecification<*>::configuration).all {
                prop(JakartaConsumerConfiguration::queues).hasSize(2)
                prop(JakartaConsumerConfiguration::topics).hasSize(0)
                prop(JakartaConsumerConfiguration::topicConnectionFactory).isNull()
            }
            transform { it.metrics }.all {
                prop(JakartaConsumerMetricsConfiguration::bytesCount).isFalse()
                prop(JakartaConsumerMetricsConfiguration::recordsCount).isFalse()
            }
            transform { it.singletonConfiguration }.all {
                prop(SingletonConfiguration::type).isEqualTo(SingletonType.UNICAST)
                prop(SingletonConfiguration::bufferSize).isEqualTo(-1)
                prop(SingletonConfiguration::idleTimeout).isEqualTo(Duration.ZERO)
            }
        }

        val step = scenario.rootSteps[0] as JakartaConsumerStepSpecification<*>
        val queueConnectionFactory = step.configuration.getProperty<() -> String>("queueConnectionFactory")
        assertThat(queueConnectionFactory.invoke()).isEqualTo(queueConnection)
    }

    @Test
    internal fun `should apply topic connection`() {
        val scenario = TestScenarioFactory.scenario("my-scenario") as StepSpecificationRegistry
        scenario.jakarta().consume {
            topicConnection { topicConnection }
            topics("topic-1", "topic-2")
        }

        assertThat(scenario.rootSteps[0]).isInstanceOf(JakartaConsumerStepSpecification::class).all {
            prop(JakartaConsumerStepSpecification<*>::configuration).all {
                prop(JakartaConsumerConfiguration::queues).hasSize(0)
                prop(JakartaConsumerConfiguration::topics).hasSize(2)
                prop(JakartaConsumerConfiguration::queueConnectionFactory).isNull()
            }
            transform { it.metrics }.all {
                prop(JakartaConsumerMetricsConfiguration::bytesCount).isFalse()
                prop(JakartaConsumerMetricsConfiguration::recordsCount).isFalse()
            }
            transform { it.singletonConfiguration }.all {
                prop(SingletonConfiguration::type).isEqualTo(SingletonType.UNICAST)
                prop(SingletonConfiguration::bufferSize).isEqualTo(-1)
                prop(SingletonConfiguration::idleTimeout).isEqualTo(Duration.ZERO)
            }
        }

        val step = scenario.rootSteps[0] as JakartaConsumerStepSpecification<*>

        val topicConnectionFactory = step.configuration.getProperty<() -> String>("topicConnectionFactory")
        assertThat(topicConnectionFactory.invoke()).isEqualTo(topicConnection)
    }

    @Test
    internal fun `should apply bytes count`() {
        val scenario = TestScenarioFactory.scenario("my-scenario") as StepSpecificationRegistry
        scenario.jakarta().consume {
            queueConnection { queueConnection }
            queues("queue-1", "queue-2")

            metrics {
                bytesCount = true
            }
        }

        assertThat(scenario.rootSteps[0]).isInstanceOf(JakartaConsumerStepSpecification::class).all {
            transform { it.metrics }.all {
                prop(JakartaConsumerMetricsConfiguration::bytesCount).isTrue()
                prop(JakartaConsumerMetricsConfiguration::recordsCount).isFalse()
            }
        }
    }

    @Test
    internal fun `should apply records count`() {
        val scenario = TestScenarioFactory.scenario("my-scenario") as StepSpecificationRegistry
        scenario.jakarta().consume {
            queueConnection { queueConnection }
            queues("queue-1", "queue-2")

            metrics {
                recordsCount = true
            }
        }

        assertThat(scenario.rootSteps[0]).isInstanceOf(JakartaConsumerStepSpecification::class).all {
            transform { it.metrics }.all {
                prop(JakartaConsumerMetricsConfiguration::bytesCount).isFalse()
                prop(JakartaConsumerMetricsConfiguration::recordsCount).isTrue()
            }
        }
    }

    companion object {

        @Container
        @JvmStatic
        private val container = GenericContainer<Nothing>(Constants.DOCKER_IMAGE).apply {
            withExposedPorts(61616)
            withCreateContainerCmdModifier {
                it.hostConfig!!.withMemory(256 * 1024.0.pow(2).toLong()).withCpuCount(1)
            }
            withEnv(Constants.CONTAINER_USER_NAME_ENV_KEY,Constants.CONTAINER_USER_NAME)
            withEnv(Constants.CONTAINER_PASSWORD_ENV_KEY,Constants.CONTAINER_PASSWORD)
        }
    }

}
