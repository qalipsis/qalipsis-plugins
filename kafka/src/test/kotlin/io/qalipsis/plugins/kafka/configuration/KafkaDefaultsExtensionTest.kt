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

package io.qalipsis.plugins.kafka.configuration

import assertk.all
import assertk.assertThat
import assertk.assertions.containsOnly
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
