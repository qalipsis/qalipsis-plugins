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

package io.qalipsis.plugins.rabbitmq.producer

import assertk.all
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isTrue
import assertk.assertions.prop
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.scenario.StepSpecificationRegistry
import io.qalipsis.api.scenario.TestScenarioFactory
import io.qalipsis.api.steps.DummyStepSpecification
import io.qalipsis.api.steps.StepMonitoringConfiguration
import io.qalipsis.plugins.rabbitmq.configuration.RabbitMqConnectionConfiguration
import io.qalipsis.plugins.rabbitmq.configuration.defaults
import io.qalipsis.plugins.rabbitmq.rabbitmq
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.jupiter.api.Test

/**
 *
 * @author Alexander Sosnovsky
 * */
@ExperimentalCoroutinesApi
internal class RabbitMqProducerStepSpecificationTest {

    @Test
    internal fun `should add minimal specification to the scenario with default values`() {

        val previousStep = DummyStepSpecification()
        previousStep.rabbitmq().produce {
            name = "my-producer-step"
        }

        assertThat(previousStep.nextSteps[0]).isInstanceOf(RabbitMqProducerStepSpecificationImpl::class).all {
            prop("name") { RabbitMqProducerStepSpecificationImpl<*>::name.call(it) }.isEqualTo("my-producer-step")

            prop(RabbitMqProducerStepSpecificationImpl<*>::connectionConfiguration).all {
                prop(RabbitMqConnectionConfiguration::host).isEqualTo("localhost")
                prop(RabbitMqConnectionConfiguration::port).isEqualTo(5672)
                prop(RabbitMqConnectionConfiguration::password).isEqualTo("guest")
                prop(RabbitMqConnectionConfiguration::username).isEqualTo("guest")
                prop(RabbitMqConnectionConfiguration::virtualHost).isEqualTo("/")
                prop(RabbitMqConnectionConfiguration::clientProperties).isEqualTo(emptyMap())
            }

            prop(RabbitMqProducerStepSpecificationImpl<*>::concurrency).isEqualTo(1)

        }
    }

    @Test
    internal fun `should add a complete specification to the scenario`() {
        val rec1 = RabbitMqProducerRecord(
            exchange = "dest-1",
            routingKey = "key-1",
            props = null,
            value = "text-1".toByteArray()
        )
        val rec2 = RabbitMqProducerRecord(
            exchange = "dest-2",
            routingKey = "key-2",
            props = null,
            value = "text-2".toByteArray()
        )

        val recordSupplier: (suspend (ctx: StepContext<*, *>, input: Any?) -> List<RabbitMqProducerRecord>) =
            { _, _ -> listOf(rec1, rec2) }

        val previousStep = DummyStepSpecification()
        previousStep.rabbitmq().produce {
            name = "my-producer-step"
            concurrency(10)
            connection {
                host = "anotherhost"
                port = 5673
                password = "pass"
                username = "test"
                virtualHost = "/guest"
                clientProperties = mapOf("t" to "test")
            }
            records(recordSupplier)
        }

        assertThat(previousStep.nextSteps[0]).isInstanceOf(RabbitMqProducerStepSpecificationImpl::class).all {
            prop("name") { RabbitMqProducerStepSpecificationImpl<*>::name.call(it) }.isEqualTo("my-producer-step")

            prop(RabbitMqProducerStepSpecificationImpl<*>::connectionConfiguration).all {
                prop(RabbitMqConnectionConfiguration::host).isEqualTo("anotherhost")
                prop(RabbitMqConnectionConfiguration::port).isEqualTo(5673)
                prop(RabbitMqConnectionConfiguration::password).isEqualTo("pass")
                prop(RabbitMqConnectionConfiguration::username).isEqualTo("test")
                prop(RabbitMqConnectionConfiguration::virtualHost).isEqualTo("/guest")
                prop(RabbitMqConnectionConfiguration::clientProperties).isEqualTo(mapOf("t" to "test"))
            }

            prop(RabbitMqProducerStepSpecificationImpl<*>::concurrency).isEqualTo(10)

            prop(RabbitMqProducerStepSpecificationImpl<*>::recordsFactory).isEqualTo(recordSupplier)
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
        }) as StepSpecificationRegistry
        val previousStep = DummyStepSpecification()
        previousStep.scenario = scenario
        previousStep.rabbitmq().produce {
            name = "my-producer-step"
        }

        assertThat(previousStep.nextSteps[0]).isInstanceOf(RabbitMqProducerStepSpecificationImpl::class).all {
            prop("name") { RabbitMqProducerStepSpecificationImpl<*>::name.call(it) }.isEqualTo("my-producer-step")

            prop(RabbitMqProducerStepSpecificationImpl<*>::connectionConfiguration).all {
                prop(RabbitMqConnectionConfiguration::host).isEqualTo("default-host")
                prop(RabbitMqConnectionConfiguration::port).isEqualTo(5673)
                prop(RabbitMqConnectionConfiguration::username).isEqualTo("admin")
                prop(RabbitMqConnectionConfiguration::password).isEqualTo("secret")
                prop(RabbitMqConnectionConfiguration::virtualHost).isEqualTo("/default")
                prop(RabbitMqConnectionConfiguration::clientProperties).isEqualTo(mapOf("app" to "qalipsis"))
            }

            prop(RabbitMqProducerStepSpecificationImpl<*>::monitoring).all {
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
                }
                monitoring {
                    events = true
                    meters = true
                }
            }
        }) as StepSpecificationRegistry

        val previousStep = DummyStepSpecification()
        previousStep.scenario = scenario
        previousStep.rabbitmq().produce {
            name = "my-producer-step"
            connection {
                host = "override-host"
                // port not set -> inherits 5673 from defaults
            }
            monitoring {
                events = false
                // meters not set -> inherits true from defaults
            }
        }

        assertThat(previousStep.nextSteps[0]).isInstanceOf(RabbitMqProducerStepSpecificationImpl::class).all {
            prop("name") { RabbitMqProducerStepSpecificationImpl<*>::name.call(it) }.isEqualTo("my-producer-step")

            prop(RabbitMqProducerStepSpecificationImpl<*>::connectionConfiguration).all {
                prop(RabbitMqConnectionConfiguration::host).isEqualTo("override-host")
                prop(RabbitMqConnectionConfiguration::port).isEqualTo(5673)
                prop(RabbitMqConnectionConfiguration::username).isEqualTo("admin")
                prop(RabbitMqConnectionConfiguration::password).isEqualTo("secret")
            }

            prop(RabbitMqProducerStepSpecificationImpl<*>::monitoring).all {
                prop(StepMonitoringConfiguration::events).isFalse()
                prop(StepMonitoringConfiguration::meters).isTrue()
            }
        }
    }

    @Test
    internal fun `should allow overriding defaults from defaults step then from RabbitMqDefaultsStepSpecification`() {
        val scenario = TestScenarioFactory.scenario("my-scenario", {
            rabbitmq().defaults {
                connection {
                    host = "default-host"
                    port = 5673
                    username = "admin"
                    password = "secret"
                }
                monitoring {
                    events = true
                    meters = true
                }
            }
        }) as StepSpecificationRegistry

        val previousStep = DummyStepSpecification()
        previousStep.scenario = scenario
        previousStep
            .rabbitmq().defaults {
                connection {
                    host = "default-host-2"
                    port = 5674
                }
            }
            .rabbitmq().produce {
                name = "my-producer-step"
                connection {
                    host = "override-host"
                    // port not set -> inherits 5673 from defaults
                }
                monitoring {
                    events = false
                    // meters not set -> inherits true from defaults
                }
            }

        assertThat(previousStep.nextSteps[0]).isInstanceOf(RabbitMqProducerStepSpecificationImpl::class).all {
            prop("name") { RabbitMqProducerStepSpecificationImpl<*>::name.call(it) }.isEqualTo("my-producer-step")

            prop(RabbitMqProducerStepSpecificationImpl<*>::connectionConfiguration).all {
                prop(RabbitMqConnectionConfiguration::host).isEqualTo("override-host")
                prop(RabbitMqConnectionConfiguration::port).isEqualTo(5674)
                prop(RabbitMqConnectionConfiguration::username).isEqualTo("admin")
                prop(RabbitMqConnectionConfiguration::password).isEqualTo("secret")
            }

            prop(RabbitMqProducerStepSpecificationImpl<*>::monitoring).all {
                prop(StepMonitoringConfiguration::events).isFalse()
                prop(StepMonitoringConfiguration::meters).isTrue()
            }
        }
    }
}
