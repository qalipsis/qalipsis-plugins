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

package io.qalipsis.plugins.rabbitmq.consumer

import assertk.all
import assertk.assertThat
import assertk.assertions.*
import io.qalipsis.api.messaging.deserializer.MessageJsonDeserializer
import io.qalipsis.api.messaging.deserializer.MessageStringDeserializer
import io.qalipsis.api.scenario.StepSpecificationRegistry
import io.qalipsis.api.scenario.TestScenarioFactory
import io.qalipsis.api.steps.SingletonConfiguration
import io.qalipsis.api.steps.SingletonType
import io.qalipsis.plugins.rabbitmq.configuration.RabbitMqConnectionConfiguration
import io.qalipsis.plugins.rabbitmq.rabbitmq
import org.junit.jupiter.api.Test
import java.time.Duration


/**
 *
 * @author Gabriel Moraes
 */
internal class RabbitMqConsumerStepSpecificationTest {

    @Test
    internal fun `should add minimal specification to the scenario with default values`() {
        val scenario = TestScenarioFactory.scenario("my-scenario") as StepSpecificationRegistry
        scenario.rabbitmq().consume {
            name = "my-step"
            queue("test")
        }

        assertThat(scenario.rootSteps.first()).isInstanceOf(RabbitMqConsumerStepSpecificationImpl::class).all {
            prop(RabbitMqConsumerStepSpecificationImpl<*>::name).isEqualTo("my-step")
            prop(RabbitMqConsumerStepSpecificationImpl<*>::queueName).isEqualTo("test")
            prop(RabbitMqConsumerStepSpecificationImpl<*>::connectionConfiguration).all {
                prop(RabbitMqConnectionConfiguration::host).isEqualTo("localhost")
                prop(RabbitMqConnectionConfiguration::port).isEqualTo(5672)
                prop(RabbitMqConnectionConfiguration::password).isEqualTo("guest")
                prop(RabbitMqConnectionConfiguration::username).isEqualTo("guest")
                prop(RabbitMqConnectionConfiguration::virtualHost).isEqualTo("/")
                prop(RabbitMqConnectionConfiguration::clientProperties).isEqualTo(emptyMap())
            }

            prop(RabbitMqConsumerStepSpecificationImpl<*>::concurrency).isEqualTo(1)
            prop(RabbitMqConsumerStepSpecificationImpl<*>::singletonConfiguration).all {
                prop(SingletonConfiguration::type).isEqualTo(SingletonType.UNICAST)
                prop(SingletonConfiguration::bufferSize).isEqualTo(-1)
                prop(SingletonConfiguration::idleTimeout).isEqualTo(Duration.ZERO)
            }
            prop(RabbitMqConsumerStepSpecificationImpl<*>::valueDeserializer).isInstanceOf(MessageStringDeserializer::class)
            prop(RabbitMqConsumerStepSpecificationImpl<*>::prefetchCount).isEqualTo(10)
        }
    }

    @Test
    internal fun `should add a complete specification to the scenario as broadcast`() {
        val scenario = TestScenarioFactory.scenario("my-scenario") as StepSpecificationRegistry
        scenario.rabbitmq().consume {
            name = "my-complete-step"
            queue("complete-test")
            concurrency(10)
            prefetchCount(20)
            connection {
                host = "anotherhost"
                port = 5673
                password = "pass"
                username = "test"
                virtualHost = "/guest"
                clientProperties = mapOf("t" to "test")
            }
            unicast(6, Duration.ofDays(1))
        }

        assertThat(scenario.rootSteps.first()).isInstanceOf(RabbitMqConsumerStepSpecificationImpl::class).all {
            prop(RabbitMqConsumerStepSpecificationImpl<*>::name).isEqualTo("my-complete-step")
            prop(RabbitMqConsumerStepSpecificationImpl<*>::queueName).isEqualTo("complete-test")
            prop(RabbitMqConsumerStepSpecificationImpl<*>::connectionConfiguration).all {
                prop(RabbitMqConnectionConfiguration::host).isEqualTo("anotherhost")
                prop(RabbitMqConnectionConfiguration::port).isEqualTo(5673)
                prop(RabbitMqConnectionConfiguration::password).isEqualTo("pass")
                prop(RabbitMqConnectionConfiguration::username).isEqualTo("test")
                prop(RabbitMqConnectionConfiguration::virtualHost).isEqualTo("/guest")
                prop(RabbitMqConnectionConfiguration::clientProperties).isEqualTo(mapOf("t" to "test"))
            }

            prop(RabbitMqConsumerStepSpecificationImpl<*>::concurrency).isEqualTo(10)
            prop(RabbitMqConsumerStepSpecificationImpl<*>::singletonConfiguration).all {
                prop(SingletonConfiguration::type).isEqualTo(SingletonType.UNICAST)
                prop(SingletonConfiguration::bufferSize).isEqualTo(6)
                prop(SingletonConfiguration::idleTimeout).isEqualTo(Duration.ofDays(1))
            }
            prop(RabbitMqConsumerStepSpecificationImpl<*>::valueDeserializer).isInstanceOf(MessageStringDeserializer::class)
            prop(RabbitMqConsumerStepSpecificationImpl<*>::prefetchCount).isEqualTo(20)
        }
    }

    @Test
    internal fun `should keep default values and use another deserialization`() {
        val scenario = TestScenarioFactory.scenario("my-scenario") as StepSpecificationRegistry
        scenario.rabbitmq().consume {
            name = "my-step"
            queue("test")
        }.deserialize(MessageJsonDeserializer(String::class))

        assertThat(scenario.rootSteps.first()).isInstanceOf(RabbitMqConsumerStepSpecificationImpl::class).all {
            prop(RabbitMqConsumerStepSpecificationImpl<*>::name).isEqualTo("my-step")
            prop(RabbitMqConsumerStepSpecificationImpl<*>::queueName).isEqualTo("test")
            prop(RabbitMqConsumerStepSpecificationImpl<*>::connectionConfiguration).all {
                prop(RabbitMqConnectionConfiguration::host).isEqualTo("localhost")
                prop(RabbitMqConnectionConfiguration::port).isEqualTo(5672)
                prop(RabbitMqConnectionConfiguration::password).isEqualTo("guest")
                prop(RabbitMqConnectionConfiguration::username).isEqualTo("guest")
                prop(RabbitMqConnectionConfiguration::virtualHost).isEqualTo("/")
                prop(RabbitMqConnectionConfiguration::clientProperties).isEqualTo(emptyMap())
            }
            prop(RabbitMqConsumerStepSpecificationImpl<*>::valueDeserializer).isInstanceOf(MessageJsonDeserializer::class)
        }
    }
}