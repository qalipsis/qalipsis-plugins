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

package io.qalipsis.plugins.cassandra.search

import assertk.all
import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.index
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.isTrue
import assertk.assertions.prop
import io.aerisconsulting.catadioptre.getProperty
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.scenario.StepSpecificationRegistry
import io.qalipsis.api.scenario.TestScenarioFactory
import io.qalipsis.api.steps.DummyStepSpecification
import io.qalipsis.api.steps.StepMonitoringConfiguration
import io.qalipsis.plugins.cassandra.cassandra
import io.qalipsis.plugins.cassandra.configuration.CassandraServerConfiguration
import io.qalipsis.plugins.cassandra.configuration.DefaultValues
import io.qalipsis.plugins.cassandra.configuration.DriverProfile
import io.qalipsis.plugins.cassandra.configuration.defaults
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.relaxedMockk
import java.math.BigDecimal
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension


/**
 *
 * @author Gabriel Moraes
 */
internal class CassandraSearchStepSpecificationImplTest {

    @JvmField
    @RegisterExtension
    val testDispatcherProvider = TestDispatcherProvider()

    @Test
    fun `should add minimal configuration for the step`() = testDispatcherProvider.runTest {
        val previousStep = DummyStepSpecification()
        previousStep.cassandra().search {
            name = "my-search-step"
            connect {
                keyspace = "test_keyspace"
                datacenterProfile = DriverProfile.LOCAL
                datacenterName = "test_datacenter"
            }
            query { _, _ ->
                "SELECT * FROM TRACKER"
            }
        }

        assertThat(previousStep.nextSteps[0]).isInstanceOf(CassandraSearchStepSpecificationImpl::class).all {
            prop("name") { CassandraSearchStepSpecificationImpl<*>::name.call(it) }.isEqualTo("my-search-step")
            prop(CassandraSearchStepSpecificationImpl<*>::serversConfig).all {
                prop(CassandraServerConfiguration::keyspace).isEqualTo("test_keyspace")
                prop(CassandraServerConfiguration::datacenterProfile).isEqualTo(DriverProfile.LOCAL)
                prop(CassandraServerConfiguration::datacenterName).isEqualTo("test_datacenter")
                prop(CassandraServerConfiguration::servers).all {
                    hasSize(1)
                    index(0).isEqualTo(DefaultValues.server)
                }
            }
            prop(CassandraSearchStepSpecificationImpl<*>::queryFactory).isNotNull()
            prop(CassandraSearchStepSpecificationImpl<*>::monitoringConfig).isNotNull()
            prop(CassandraSearchStepSpecificationImpl<*>::parametersFactory).isNotNull()
            prop(CassandraSearchStepSpecificationImpl<*>::monitoringConfig).all {
                prop(StepMonitoringConfiguration::meters).isTrue()
                prop(StepMonitoringConfiguration::events).isTrue()
            }
        }

        val queryFactory =
            previousStep.nextSteps[0].getProperty<suspend (ctx: StepContext<*, *>, input: Int) -> String>(
                "queryFactory")
        assertThat(queryFactory(relaxedMockk(), relaxedMockk())).isEqualTo("""SELECT * FROM TRACKER""")

        val paramsFactory =
            previousStep.nextSteps[0].getProperty<suspend (ctx: StepContext<*, *>, input: Int) -> List<Any>>(
                "parametersFactory")
        assertThat(paramsFactory(relaxedMockk(), relaxedMockk())).hasSize(0)
    }

    @Test
    fun `should add a complete configuration for the step`() = testDispatcherProvider.runTest {
        val previousStep = DummyStepSpecification()
        previousStep.cassandra().search {
            name = "my-search-step"
            connect {
                keyspace = "test_keyspace"
                datacenterProfile = DriverProfile.PEER
                datacenterName = "test_datacenter"
                servers = listOf("localhost:27017", "localhost:27018")
            }
            query { _, _ ->
                "SELECT * FROM TRACKER"
            }

            parameters { _, _ ->
                listOf("1", BigDecimal.TEN, 123)
            }

            monitoring {
                meters = true
                events = true
            }

        }

        assertThat(previousStep.nextSteps[0]).isInstanceOf(CassandraSearchStepSpecificationImpl::class).all {
            prop("name") { CassandraSearchStepSpecificationImpl<*>::name.call(it) }.isEqualTo("my-search-step")
            prop(CassandraSearchStepSpecificationImpl<*>::serversConfig).all {
                prop(CassandraServerConfiguration::keyspace).isEqualTo("test_keyspace")
                prop(CassandraServerConfiguration::datacenterProfile).isEqualTo(DriverProfile.PEER)
                prop(CassandraServerConfiguration::datacenterName).isEqualTo("test_datacenter")
                prop(CassandraServerConfiguration::servers).all {
                    hasSize(2)
                    index(0).isEqualTo("localhost:27017")
                    index(1).isEqualTo("localhost:27018")
                }
            }
            prop(CassandraSearchStepSpecificationImpl<*>::queryFactory).isNotNull()
            prop(CassandraSearchStepSpecificationImpl<*>::parametersFactory).isNotNull()
            prop(CassandraSearchStepSpecificationImpl<*>::monitoringConfig).all {
                prop(StepMonitoringConfiguration::meters).isTrue()
                prop(StepMonitoringConfiguration::events).isTrue()
            }
        }

        val queryFactory =
            previousStep.nextSteps[0].getProperty<suspend (ctx: StepContext<*, *>, input: Int) -> String>(
                "queryFactory")
        assertThat(queryFactory(relaxedMockk(), relaxedMockk())).isEqualTo("""SELECT * FROM TRACKER""")

        val paramsFactory =
            previousStep.nextSteps[0].getProperty<suspend (ctx: StepContext<*, *>, input: Int) -> List<Any>>(
                "parametersFactory")
        assertThat(paramsFactory(relaxedMockk(), relaxedMockk())).hasSize(3)
    }

    @Test
    fun `should apply defaults from CassandraDefaultsExtension`() = testDispatcherProvider.runTest {
        val scenario = TestScenarioFactory.scenario("my-scenario", {
            cassandra().defaults {
                connect {
                    servers = listOf("default-host:9042", "default-host:9043")
                    keyspace = "default_keyspace"
                    datacenterProfile = DriverProfile.LOCAL
                    datacenterName = "default_dc"
                }
                monitoring {
                    events = true
                    meters = true
                }
            }
        }) as StepSpecificationRegistry

        val previousStep = DummyStepSpecification()
        previousStep.scenario = scenario
        previousStep.cassandra().search {
            name = "my-search-step"
            query { _, _ -> "SELECT * FROM TRACKER" }
        }

        assertThat(previousStep.nextSteps[0]).isInstanceOf(CassandraSearchStepSpecificationImpl::class).all {
            prop(CassandraSearchStepSpecificationImpl<*>::serversConfig).all {
                prop(CassandraServerConfiguration::servers).isEqualTo(listOf("default-host:9042", "default-host:9043"))
                prop(CassandraServerConfiguration::keyspace).isEqualTo("default_keyspace")
                prop(CassandraServerConfiguration::datacenterProfile).isEqualTo(DriverProfile.LOCAL)
                prop(CassandraServerConfiguration::datacenterName).isEqualTo("default_dc")
            }
            prop(CassandraSearchStepSpecificationImpl<*>::monitoringConfig).all {
                prop(StepMonitoringConfiguration::events).isTrue()
                prop(StepMonitoringConfiguration::meters).isTrue()
            }
        }
    }

    @Test
    fun `should allow overriding defaults from CassandraDefaultsExtension`() = testDispatcherProvider.runTest {
        val scenario = TestScenarioFactory.scenario("my-scenario", {
            cassandra().defaults {
                connect {
                    servers = listOf("default-host:9042")
                    keyspace = "default_keyspace"
                    datacenterProfile = DriverProfile.LOCAL
                    datacenterName = "default_dc"
                }
                monitoring {
                    events = true
                    meters = true
                }
            }
        }) as StepSpecificationRegistry

        val previousStep = DummyStepSpecification()
        previousStep.scenario = scenario
        previousStep.cassandra().search {
            name = "my-search-step"
            connect {
                servers = listOf("override-host:9042")
            }
            monitoring {
                events = false
            }
            query { _, _ -> "SELECT * FROM TRACKER" }
        }

        assertThat(previousStep.nextSteps[0]).isInstanceOf(CassandraSearchStepSpecificationImpl::class).all {
            prop(CassandraSearchStepSpecificationImpl<*>::serversConfig).all {
                prop(CassandraServerConfiguration::servers).isEqualTo(listOf("override-host:9042"))
                prop(CassandraServerConfiguration::keyspace).isEqualTo("default_keyspace")
                prop(CassandraServerConfiguration::datacenterProfile).isEqualTo(DriverProfile.LOCAL)
                prop(CassandraServerConfiguration::datacenterName).isEqualTo("default_dc")
            }
            prop(CassandraSearchStepSpecificationImpl<*>::monitoringConfig).all {
                prop(StepMonitoringConfiguration::events).isFalse()
                prop(StepMonitoringConfiguration::meters).isTrue()
            }
        }
    }

    @Test
    fun `should allow successive overwriting of defaults`() = testDispatcherProvider.runTest {
        val scenario = TestScenarioFactory.scenario("my-scenario", {
            cassandra().defaults {
                connect {
                    servers = listOf("default-host:9042")
                    keyspace = "default_keyspace"
                    datacenterProfile = DriverProfile.LOCAL
                    datacenterName = "default_dc"
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
            .cassandra().defaults {
                connect {
                    servers = listOf("step-default-host:9042")
                    keyspace = "step_keyspace"
                }
            }
            .cassandra().search {
                name = "my-search-step"
                connect {
                    servers = listOf("override-host:9042")
                }
                monitoring {
                    events = false
                    // meters not set -> inherits true from scenario-level defaults
                }
                query { _, _ -> "SELECT * FROM TRACKER" }
            }

        assertThat(previousStep.nextSteps[0]).isInstanceOf(CassandraSearchStepSpecificationImpl::class).all {
            prop(CassandraSearchStepSpecificationImpl<*>::serversConfig).all {
                prop(CassandraServerConfiguration::servers).isEqualTo(listOf("override-host:9042"))
                prop(CassandraServerConfiguration::keyspace).isEqualTo("step_keyspace")
                prop(CassandraServerConfiguration::datacenterProfile).isEqualTo(DriverProfile.LOCAL)
                prop(CassandraServerConfiguration::datacenterName).isEqualTo("default_dc")
            }
            prop(CassandraSearchStepSpecificationImpl<*>::monitoringConfig).all {
                prop(StepMonitoringConfiguration::events).isFalse()
                prop(StepMonitoringConfiguration::meters).isTrue()
            }
        }
    }
}
