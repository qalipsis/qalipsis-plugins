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

package io.qalipsis.plugins.jakarta.configuration

import assertk.all
import assertk.assertThat
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isSameInstanceAs
import assertk.assertions.isTrue
import assertk.assertions.prop
import io.qalipsis.api.steps.StepMonitoringConfiguration
import io.qalipsis.plugins.jakarta.consumer.JakartaConsumerConfiguration
import io.qalipsis.plugins.jakarta.consumer.JakartaConsumerStepSpecification
import io.qalipsis.plugins.jakarta.deserializer.JakartaStringDeserializer
import io.qalipsis.plugins.jakarta.producer.JakartaProducerStepSpecificationImpl
import jakarta.jms.Connection
import jakarta.jms.QueueConnection
import jakarta.jms.Session
import jakarta.jms.TopicConnection
import org.junit.jupiter.api.Test

/**
 * @author Eric Jesse
 */
internal class JakartaDefaultsExtensionTest {

    @Test
    fun `should apply queue connection defaults to consumer step`() {
        val defaults = JakartaDefaultsExtensionImpl()
        val queueFactory: () -> QueueConnection = { throw NotImplementedError() }
        val sessionFactory: (Connection) -> Session = { throw NotImplementedError() }
        defaults.queueConnection(queueFactory)
        defaults.session(sessionFactory)
        defaults.monitoring {
            events = true
            meters = true
        }

        val spec = JakartaConsumerStepSpecification(JakartaStringDeserializer())
        defaults.applyTo(spec)

        assertThat(spec).all {
            prop(JakartaConsumerStepSpecification<*>::configuration).all {
                prop(JakartaConsumerConfiguration::queueConnectionFactory).isNotNull().isSameInstanceAs(queueFactory)
                prop(JakartaConsumerConfiguration::topicConnectionFactory).isNull()
                prop(JakartaConsumerConfiguration::sessionFactory).isSameInstanceAs(sessionFactory)
            }
            prop(JakartaConsumerStepSpecification<*>::monitoringConfig).all {
                prop(StepMonitoringConfiguration::events).isTrue()
                prop(StepMonitoringConfiguration::meters).isTrue()
            }
        }
    }

    @Test
    fun `should apply topic connection defaults to consumer step`() {
        val defaults = JakartaDefaultsExtensionImpl()
        val topicFactory: () -> TopicConnection = { throw NotImplementedError() }
        defaults.topicConnection(topicFactory)
        defaults.monitoring {
            events = true
        }

        val spec = JakartaConsumerStepSpecification(JakartaStringDeserializer())
        defaults.applyTo(spec)

        assertThat(spec).all {
            prop(JakartaConsumerStepSpecification<*>::configuration).all {
                prop(JakartaConsumerConfiguration::topicConnectionFactory).isNotNull().isSameInstanceAs(topicFactory)
                prop(JakartaConsumerConfiguration::queueConnectionFactory).isNull()
            }
            prop(JakartaConsumerStepSpecification<*>::monitoringConfig).all {
                prop(StepMonitoringConfiguration::events).isTrue()
            }
        }
    }

    @Test
    fun `should apply connection and session defaults to producer step`() {
        val defaults = JakartaDefaultsExtensionImpl()
        val connectionFactory: () -> Connection = { throw NotImplementedError() }
        val sessionFactory: (Connection) -> Session = { throw NotImplementedError() }
        defaults.connection(connectionFactory)
        defaults.session(sessionFactory)
        defaults.monitoring {
            events = true
            meters = true
        }

        val spec = JakartaProducerStepSpecificationImpl<Any>()
        defaults.applyTo(spec)

        assertThat(spec).all {
            prop(JakartaProducerStepSpecificationImpl<*>::connectionFactory).isSameInstanceAs(connectionFactory)
            prop(JakartaProducerStepSpecificationImpl<*>::sessionFactory).isSameInstanceAs(sessionFactory)
            prop(JakartaProducerStepSpecificationImpl<*>::monitoringConfig).all {
                prop(StepMonitoringConfiguration::events).isTrue()
                prop(StepMonitoringConfiguration::meters).isTrue()
            }
        }
    }

    @Test
    fun `should only apply monitoring when no connection is configured`() {
        val defaults = JakartaDefaultsExtensionImpl()
        defaults.monitoring {
            events = true
        }

        val consumerSpec = JakartaConsumerStepSpecification(JakartaStringDeserializer())
        defaults.applyTo(consumerSpec)

        assertThat(consumerSpec).all {
            prop(JakartaConsumerStepSpecification<*>::configuration).all {
                prop(JakartaConsumerConfiguration::queueConnectionFactory).isNull()
                prop(JakartaConsumerConfiguration::topicConnectionFactory).isNull()
            }
            prop(JakartaConsumerStepSpecification<*>::monitoringConfig).all {
                prop(StepMonitoringConfiguration::events).isTrue()
            }
        }

        val producerSpec = JakartaProducerStepSpecificationImpl<Any>()
        defaults.applyTo(producerSpec)

        assertThat(producerSpec).all {
            prop(JakartaProducerStepSpecificationImpl<*>::monitoringConfig).all {
                prop(StepMonitoringConfiguration::events).isTrue()
            }
        }
    }
}
