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

package io.qalipsis.plugins.cassandra.configuration

import assertk.all
import assertk.assertThat
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
import io.qalipsis.plugins.cassandra.cassandra
import org.junit.jupiter.api.Test

/**
 * @author Eric Jessé
 */
internal class CassandraDefaultsExtensionTest {

    @Test
    fun `should create defaults specification with connection and monitoring`() {
        val scenario = TestScenarioFactory.scenario("my-scenario", {
            cassandra().defaults {
                connect {
                    servers = listOf("cassandra-host:9042", "cassandra-host:9043")
                    keyspace = "test_keyspace"
                    datacenterProfile = DriverProfile.LOCAL
                    datacenterName = "test_datacenter"
                }
                monitoring {
                    events = true
                    meters = true
                }
            }
        })

        assertThat(scenario).all {
            isInstanceOf(StepSpecificationRegistry::class).prop(StepSpecificationRegistry::rootSteps).isEmpty()
            isInstanceOf(ExtensibleScenarioSpecification::class).all {
                prop("extension", { it.getExtension<CassandraDefaultsExtensionImpl>("cassandra.defaults") }).isNotNull()
                    .all {
                        prop(CassandraDefaultsExtensionImpl::serversConfig).all {
                            prop(CassandraServerConfiguration::servers).isEqualTo(
                                listOf(
                                    "cassandra-host:9042",
                                    "cassandra-host:9043"
                                )
                            )
                            prop(CassandraServerConfiguration::keyspace).isEqualTo("test_keyspace")
                            prop(CassandraServerConfiguration::datacenterProfile).isEqualTo(DriverProfile.LOCAL)
                            prop(CassandraServerConfiguration::datacenterName).isEqualTo("test_datacenter")
                        }
                        prop(CassandraDefaultsExtensionImpl::monitoringConfig).all {
                            prop(StepMonitoringConfiguration::events).isTrue()
                            prop(StepMonitoringConfiguration::meters).isTrue()
                        }
                    }
            }
        }
    }

    @Test
    fun `should create defaults specification with connection only`() {
        val scenario = TestScenarioFactory.scenario("my-scenario", {
            cassandra().defaults {
                connect {
                    servers = listOf("cassandra-host:9042")
                    keyspace = "test_keyspace"
                }
            }
        })

        assertThat(scenario).all {
            isInstanceOf(StepSpecificationRegistry::class).prop(StepSpecificationRegistry::rootSteps).isEmpty()
            isInstanceOf(ExtensibleScenarioSpecification::class).all {
                prop("extension", { it.getExtension<CassandraDefaultsExtensionImpl>("cassandra.defaults") }).isNotNull()
                    .all {
                        prop(CassandraDefaultsExtensionImpl::serversConfig).all {
                            prop(CassandraServerConfiguration::servers).isEqualTo(listOf("cassandra-host:9042"))
                            prop(CassandraServerConfiguration::keyspace).isEqualTo("test_keyspace")
                        }
                        prop(CassandraDefaultsExtensionImpl::monitoringConfig).all {
                            prop(StepMonitoringConfiguration::events).isTrue()
                            prop(StepMonitoringConfiguration::meters).isTrue()
                        }
                    }
            }
        }
    }

    @Test
    fun `should create defaults specification with disabled monitoring`() {
        val scenario = TestScenarioFactory.scenario("my-scenario", {
            cassandra().defaults {
                monitoring { off() }
            }
        })

        assertThat(scenario).all {
            isInstanceOf(StepSpecificationRegistry::class).prop(StepSpecificationRegistry::rootSteps).isEmpty()
            isInstanceOf(ExtensibleScenarioSpecification::class).all {
                prop("extension", { it.getExtension<CassandraDefaultsExtensionImpl>("cassandra.defaults") }).isNotNull()
                    .all {
                        prop(CassandraDefaultsExtensionImpl::serversConfig).all {
                            prop(CassandraServerConfiguration::servers).isEqualTo(listOf(DefaultValues.server))
                            prop(CassandraServerConfiguration::keyspace).isEqualTo("")
                        }
                        prop(CassandraDefaultsExtensionImpl::monitoringConfig).all {
                            prop(StepMonitoringConfiguration::events).isFalse()
                            prop(StepMonitoringConfiguration::meters).isFalse()
                        }
                    }
            }
        }
    }
}
