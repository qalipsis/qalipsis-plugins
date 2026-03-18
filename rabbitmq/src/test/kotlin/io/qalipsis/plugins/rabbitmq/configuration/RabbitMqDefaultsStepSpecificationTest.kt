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

package io.qalipsis.plugins.rabbitmq.configuration

import assertk.all
import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.isTrue
import assertk.assertions.prop
import io.qalipsis.api.scenario.ExtensibleScenarioSpecification
import io.qalipsis.api.scenario.StepSpecificationRegistry
import io.qalipsis.api.scenario.TestScenarioFactory
import io.qalipsis.api.steps.StepMonitoringConfiguration
import io.qalipsis.plugins.rabbitmq.rabbitmq
import org.junit.jupiter.api.Test

/**
 * @author Eric Jessé
 */
internal class RabbitMqDefaultsStepSpecificationTest {

    @Test
    fun `should create defaults specification with connection and monitoring`() {
        // when
        val scenario = TestScenarioFactory.scenario("my-scenario", {
            rabbitmq().defaults {
                connection {
                    host = "rabbitmq-host"
                    port = 5673
                    username = "admin"
                    password = "secret"
                    virtualHost = "/test"
                    clientProperties = mapOf("app" to "qalipsis")
                }
                monitoring {
                    events = true
                    meters = true
                }
            }
        })

        // then
        assertThat(scenario).all {
            isInstanceOf(StepSpecificationRegistry::class).prop(StepSpecificationRegistry::rootSteps).isEmpty()
            isInstanceOf(ExtensibleScenarioSpecification::class).all {
                prop("extension", { it.getExtension<RabbitMqDefaultsExtensionImpl>("rabbitmq.defaults") }).isNotNull()
                    .all {
                        prop(RabbitMqDefaultsExtensionImpl::connectionConfiguration).all {
                            prop(RabbitMqConnectionConfiguration::host).isEqualTo("rabbitmq-host")
                            prop(RabbitMqConnectionConfiguration::port).isEqualTo(5673)
                            prop(RabbitMqConnectionConfiguration::username).isEqualTo("admin")
                            prop(RabbitMqConnectionConfiguration::password).isEqualTo("secret")
                            prop(RabbitMqConnectionConfiguration::virtualHost).isEqualTo("/test")
                            prop(RabbitMqConnectionConfiguration::clientProperties).isEqualTo(mapOf("app" to "qalipsis"))
                        }
                        prop(RabbitMqDefaultsExtensionImpl::monitoringConfig).all {
                            prop(StepMonitoringConfiguration::events).isTrue()
                            prop(StepMonitoringConfiguration::meters).isTrue()
                        }
                    }
            }
        }
    }

    @Test
    fun `should create defaults specification with connection only`() {
        // when
        val scenario = TestScenarioFactory.scenario("my-scenario", {
            rabbitmq().defaults {
                connection {
                    host = "rabbitmq-host"
                    port = 5673
                }
            }
        })

        // then
        assertThat(scenario).all {
            isInstanceOf(StepSpecificationRegistry::class).prop(StepSpecificationRegistry::rootSteps).isEmpty()
            isInstanceOf(ExtensibleScenarioSpecification::class).all {
                prop(
                    "extension",
                    { it.getExtension<RabbitMqDefaultsExtensionImpl>("rabbitmq.defaults") }).isNotNull()
                    .all {
                        prop(RabbitMqDefaultsExtensionImpl::connectionConfiguration).all {
                            prop(RabbitMqConnectionConfiguration::host).isEqualTo("rabbitmq-host")
                            prop(RabbitMqConnectionConfiguration::port).isEqualTo(5673)
                        }
                        prop(RabbitMqDefaultsExtensionImpl::monitoringConfig).all {
                            prop(StepMonitoringConfiguration::events).isTrue()
                            prop(StepMonitoringConfiguration::meters).isTrue()
                        }
                    }
            }
        }
    }

    @Test
    fun `should create defaults specification with monitoring only`() {
        // when
        val scenario = TestScenarioFactory.scenario("my-scenario", {
            rabbitmq().defaults {
                monitoring {
                    events = true
                    meters = true
                }
            }
        })

        // then
        assertThat(scenario).all {
            isInstanceOf(StepSpecificationRegistry::class).prop(StepSpecificationRegistry::rootSteps).isEmpty()
            isInstanceOf(ExtensibleScenarioSpecification::class).all {
                prop(
                    "extension",
                    { it.getExtension<RabbitMqDefaultsExtensionImpl>("rabbitmq.defaults") }).isNotNull()
                    .all {
                        prop(RabbitMqDefaultsExtensionImpl::connectionConfiguration).all {
                            prop(RabbitMqConnectionConfiguration::host).isEqualTo("localhost")
                            prop(RabbitMqConnectionConfiguration::port).isEqualTo(5672)
                            prop(RabbitMqConnectionConfiguration::username).isEqualTo("guest")
                            prop(RabbitMqConnectionConfiguration::password).isEqualTo("guest")
                        }
                        prop(RabbitMqDefaultsExtensionImpl::monitoringConfig).all {
                            prop(StepMonitoringConfiguration::events).isTrue()
                            prop(StepMonitoringConfiguration::meters).isTrue()
                        }
                    }
            }
        }
    }
}
