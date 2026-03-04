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

package io.qalipsis.plugins.kafka.configuration

import assertk.all
import assertk.assertThat
import assertk.assertions.containsOnly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.isTrue
import assertk.assertions.prop
import io.qalipsis.api.scenario.ExtensibleScenarioSpecification
import io.qalipsis.api.scenario.StepSpecificationRegistry
import io.qalipsis.api.scenario.TestScenarioFactory
import io.qalipsis.api.steps.StepMonitoringConfiguration
import io.qalipsis.plugins.kafka.kafka
import org.junit.jupiter.api.Test

/**
 * @author Eric Jessé
 */
internal class KafkaDefaultsExtensionTest {

    @Test
    fun `should create defaults specification with bootstrap, properties and monitoring`() {
        // when
        val scenario = TestScenarioFactory.scenario("my-scenario", {
            kafka().defaults {
                bootstrap("kafka-host:9092", "kafka-host:9093")
                properties("security.protocol" to "SASL_SSL", "sasl.mechanism" to "PLAIN")
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
                prop("extension", { it.getExtension<KafkaDefaultsExtensionImpl>(EXTENSION_NAME) }).isNotNull()
                    .all {
                        prop(KafkaDefaultsExtensionImpl::bootstrap).isEqualTo("kafka-host:9092,kafka-host:9093")
                        prop(KafkaDefaultsExtensionImpl::properties).containsOnly(
                            "security.protocol" to "SASL_SSL",
                            "sasl.mechanism" to "PLAIN"
                        )
                        prop(KafkaDefaultsExtensionImpl::monitoringConfig).all {
                            prop(StepMonitoringConfiguration::events).isTrue()
                            prop(StepMonitoringConfiguration::meters).isTrue()
                        }
                    }
            }
        }
    }

    @Test
    fun `should create defaults specification with bootstrap only`() {
        // when
        val scenario = TestScenarioFactory.scenario("my-scenario", {
            kafka().defaults {
                bootstrap("kafka-host:9092")
            }
        })

        // then
        assertThat(scenario).all {
            isInstanceOf(StepSpecificationRegistry::class).prop(StepSpecificationRegistry::rootSteps).isEmpty()
            isInstanceOf(ExtensibleScenarioSpecification::class).all {
                prop("extension", { it.getExtension<KafkaDefaultsExtensionImpl>(EXTENSION_NAME) }).isNotNull()
                    .all {
                        prop(KafkaDefaultsExtensionImpl::bootstrap).isEqualTo("kafka-host:9092")
                        prop(KafkaDefaultsExtensionImpl::properties).isEmpty()
                        prop(KafkaDefaultsExtensionImpl::monitoringConfig).all {
                            prop(StepMonitoringConfiguration::events).isFalse()
                            prop(StepMonitoringConfiguration::meters).isFalse()
                        }
                    }
            }
        }
    }

    @Test
    fun `should create defaults specification with monitoring only`() {
        // when
        val scenario = TestScenarioFactory.scenario("my-scenario", {
            kafka().defaults {
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
                prop("extension", { it.getExtension<KafkaDefaultsExtensionImpl>(EXTENSION_NAME) }).isNotNull()
                    .all {
                        prop(KafkaDefaultsExtensionImpl::bootstrap).isEqualTo("localhost:9092")
                        prop(KafkaDefaultsExtensionImpl::properties).isEmpty()
                        prop(KafkaDefaultsExtensionImpl::monitoringConfig).all {
                            prop(StepMonitoringConfiguration::events).isTrue()
                            prop(StepMonitoringConfiguration::meters).isTrue()
                        }
                    }
            }
        }
    }
}
