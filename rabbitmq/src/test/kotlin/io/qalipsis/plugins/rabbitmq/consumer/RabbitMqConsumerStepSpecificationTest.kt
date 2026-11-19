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

package io.qalipsis.plugins.rabbitmq.consumer

import assertk.all
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isTrue
import assertk.assertions.prop
import io.qalipsis.api.messaging.deserializer.MessageJsonDeserializer
import io.qalipsis.api.messaging.deserializer.MessageStringDeserializer
import io.qalipsis.api.scenario.StepSpecificationRegistry
import io.qalipsis.api.scenario.TestScenarioFactory
import io.qalipsis.api.steps.SingletonConfiguration
import io.qalipsis.api.steps.SingletonType
import io.qalipsis.api.steps.StepMonitoringConfiguration
import io.qalipsis.plugins.rabbitmq.configuration.RabbitMqConnectionConfiguration
import io.qalipsis.plugins.rabbitmq.configuration.defaults
import io.qalipsis.plugins.rabbitmq.rabbitmq
import java.time.Duration
import org.junit.jupiter.api.Test


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

    @Test
    internal fun `should apply defaults from RabbitMqDefaultsStepSpecification`() {
        val scenario = TestScenarioFactory.scenario("my-scenario", {
            rabbitmq().defaults {
                connection {
                    host = "default-host"
                    port = 5673
                    username = "admin"
                    password = "secret"
                    virtualHost = "/default"
                    clientProperties = mapOf("app" to "qalipsis")
                }
                monitoring {
                    events = true
                    meters = true
                }
            }
        })

        scenario.start()
            .rabbitmq().consume {
                name = "my-step"
                queue("test")
            }

        assertThat((scenario as StepSpecificationRegistry).rootSteps[0]).isInstanceOf(
            RabbitMqConsumerStepSpecificationImpl::class
        ).all {
            prop(RabbitMqConsumerStepSpecificationImpl<*>::name).isEqualTo("my-step")
            prop(RabbitMqConsumerStepSpecificationImpl<*>::queueName).isEqualTo("test")
            prop(RabbitMqConsumerStepSpecificationImpl<*>::connectionConfiguration).all {
                prop(RabbitMqConnectionConfiguration::host).isEqualTo("default-host")
                prop(RabbitMqConnectionConfiguration::port).isEqualTo(5673)
                prop(RabbitMqConnectionConfiguration::username).isEqualTo("admin")
                prop(RabbitMqConnectionConfiguration::password).isEqualTo("secret")
                prop(RabbitMqConnectionConfiguration::virtualHost).isEqualTo("/default")
                prop(RabbitMqConnectionConfiguration::clientProperties).isEqualTo(mapOf("app" to "qalipsis"))
            }
            prop(RabbitMqConsumerStepSpecificationImpl<*>::monitoringConfig).all {
                prop(StepMonitoringConfiguration::events).isTrue()
                prop(StepMonitoringConfiguration::meters).isTrue()
            }
        }
    }

    @Test
    internal fun `should allow overriding defaults from RabbitMqDefaultsStepSpecification`() {
        val scenario = TestScenarioFactory.scenario("my-scenario", {
            rabbitmq().defaults {
                connection {
                    host = "default-host"
                    port = 5673
                    username = "admin"
                    password = "secret"
                    virtualHost = "/default"
                }
                monitoring {
                    events = true
                    meters = true
                }
            }
        })

        scenario.start().rabbitmq().consume {
            name = "my-step"
            queue("test")
            connection {
                host = "override-host"
                // port not set -> inherits 5673 from defaults
            }
            monitoring {
                events = false
                // meters not set -> inherits true from defaults
            }
        }

        assertThat(
            assertThat((scenario as StepSpecificationRegistry).rootSteps[0]).isInstanceOf(
                RabbitMqConsumerStepSpecificationImpl::class
            ).all {
                prop(RabbitMqConsumerStepSpecificationImpl<*>::name).isEqualTo("my-step")
                prop(RabbitMqConsumerStepSpecificationImpl<*>::connectionConfiguration).all {
                    prop(RabbitMqConnectionConfiguration::host).isEqualTo("override-host")
                    prop(RabbitMqConnectionConfiguration::port).isEqualTo(5673)
                    prop(RabbitMqConnectionConfiguration::username).isEqualTo("admin")
                    prop(RabbitMqConnectionConfiguration::password).isEqualTo("secret")
                    prop(RabbitMqConnectionConfiguration::virtualHost).isEqualTo("/default")
                }
                prop(RabbitMqConsumerStepSpecificationImpl<*>::monitoringConfig).all {
                    prop(StepMonitoringConfiguration::events).isFalse()
                    prop(StepMonitoringConfiguration::meters).isTrue()
                }
            }
        )
    }

}