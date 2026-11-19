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

package io.qalipsis.plugins.jms.configuration

import assertk.all
import assertk.assertThat
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isSameInstanceAs
import assertk.assertions.isTrue
import assertk.assertions.prop
import io.qalipsis.api.steps.StepMonitoringConfiguration
import io.qalipsis.plugins.jms.consumer.JmsConsumerConfiguration
import io.qalipsis.plugins.jms.consumer.JmsConsumerStepSpecification
import io.qalipsis.plugins.jms.deserializer.JmsStringDeserializer
import io.qalipsis.plugins.jms.producer.JmsProducerStepSpecificationImpl
import javax.jms.Connection
import javax.jms.QueueConnection
import javax.jms.TopicConnection
import org.junit.jupiter.api.Test

/**
 * @author Eric Jesse
 */
internal class JmsDefaultsExtensionTest {

    @Test
    fun `should apply queue connection defaults to consumer step`() {
        val defaults = JmsDefaultsExtensionImpl()
        val queueFactory: () -> QueueConnection = { throw NotImplementedError() }
        defaults.queueConnection(queueFactory)
        defaults.monitoring {
            events = true
            meters = true
        }

        val spec = JmsConsumerStepSpecification(JmsStringDeserializer())
        defaults.applyTo(spec)

        assertThat(spec).all {
            prop(JmsConsumerStepSpecification<*>::configuration).all {
                prop(JmsConsumerConfiguration::queueConnectionFactory).isNotNull().isSameInstanceAs(queueFactory)
                prop(JmsConsumerConfiguration::topicConnectionFactory).isNull()
            }
            prop(JmsConsumerStepSpecification<*>::monitoringConfig).all {
                prop(StepMonitoringConfiguration::events).isTrue()
                prop(StepMonitoringConfiguration::meters).isTrue()
            }
        }
    }

    @Test
    fun `should apply topic connection defaults to consumer step`() {
        val defaults = JmsDefaultsExtensionImpl()
        val topicFactory: () -> TopicConnection = { throw NotImplementedError() }
        defaults.topicConnection(topicFactory)
        defaults.monitoring {
            events = true
        }

        val spec = JmsConsumerStepSpecification(JmsStringDeserializer())
        defaults.applyTo(spec)

        assertThat(spec).all {
            prop(JmsConsumerStepSpecification<*>::configuration).all {
                prop(JmsConsumerConfiguration::topicConnectionFactory).isNotNull().isSameInstanceAs(topicFactory)
                prop(JmsConsumerConfiguration::queueConnectionFactory).isNull()
            }
            prop(JmsConsumerStepSpecification<*>::monitoringConfig).all {
                prop(StepMonitoringConfiguration::events).isTrue()
            }
        }
    }

    @Test
    fun `should apply connection defaults to producer step`() {
        val defaults = JmsDefaultsExtensionImpl()
        val connectionFactory: () -> Connection = { throw NotImplementedError() }
        defaults.connection(connectionFactory)
        defaults.monitoring {
            events = true
            meters = true
        }

        val spec = JmsProducerStepSpecificationImpl<Any>()
        defaults.applyTo(spec)

        assertThat(spec).all {
            prop(JmsProducerStepSpecificationImpl<*>::connectionFactory).isSameInstanceAs(connectionFactory)
            prop(JmsProducerStepSpecificationImpl<*>::monitoringConfig).all {
                prop(StepMonitoringConfiguration::events).isTrue()
                prop(StepMonitoringConfiguration::meters).isTrue()
            }
        }
    }

    @Test
    fun `should only apply monitoring when no connection is configured`() {
        val defaults = JmsDefaultsExtensionImpl()
        defaults.monitoring {
            events = true
        }

        val consumerSpec = JmsConsumerStepSpecification(JmsStringDeserializer())
        defaults.applyTo(consumerSpec)

        assertThat(consumerSpec).all {
            prop(JmsConsumerStepSpecification<*>::configuration).all {
                prop(JmsConsumerConfiguration::queueConnectionFactory).isNull()
                prop(JmsConsumerConfiguration::topicConnectionFactory).isNull()
            }
            prop(JmsConsumerStepSpecification<*>::monitoringConfig).all {
                prop(StepMonitoringConfiguration::events).isTrue()
            }
        }

        val producerSpec = JmsProducerStepSpecificationImpl<Any>()
        defaults.applyTo(producerSpec)

        assertThat(producerSpec).all {
            prop(JmsProducerStepSpecificationImpl<*>::monitoringConfig).all {
                prop(StepMonitoringConfiguration::events).isTrue()
            }
        }
    }
}
