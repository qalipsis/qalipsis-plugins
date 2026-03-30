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

package io.qalipsis.plugins.cassandra.save

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
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension


/**
 *
 * @author Svetlana Paliashchuk
 */
internal class CassandraSaveStepSpecificationImplTest {

    @JvmField
    @RegisterExtension
    val testDispatcherProvider = TestDispatcherProvider()

    @Test
    fun `should add minimal configuration for the step`() = testDispatcherProvider.runTest {
        val previousStep = DummyStepSpecification()
        previousStep.cassandra().save {
            name = "my-save-step"
            connect {
                keyspace = "test_keyspace"
                datacenterProfile = DriverProfile.LOCAL
                datacenterName = "test_datacenter"
            }
            table { _, _ ->
                "test"
            }
            columns { _, _ ->
                listOf("column")
            }
            rows { _, _ ->
                listOf(CassandraSaveRow())
            }
        }

        assertThat(previousStep.nextSteps[0]).isInstanceOf(CassandraSaveStepSpecificationImpl::class).all {
            prop("name") { CassandraSaveStepSpecificationImpl<*>::name.call(it) }.isEqualTo("my-save-step")
            prop(CassandraSaveStepSpecificationImpl<*>::serversConfig).all {
                prop(CassandraServerConfiguration::keyspace).isEqualTo("test_keyspace")
                prop(CassandraServerConfiguration::datacenterProfile).isEqualTo(DriverProfile.LOCAL)
                prop(CassandraServerConfiguration::datacenterName).isEqualTo("test_datacenter")
                prop(CassandraServerConfiguration::servers).all {
                    hasSize(1)
                    index(0).isEqualTo(DefaultValues.server)
                }
            }
            prop(CassandraSaveStepSpecificationImpl<*>::tableName).isNotNull()
            prop(CassandraSaveStepSpecificationImpl<*>::columnsConfig).isNotNull()
            prop(CassandraSaveStepSpecificationImpl<*>::rowsFactory).isNotNull()
            prop(CassandraSaveStepSpecificationImpl<*>::monitoringConfig).all {
                prop(StepMonitoringConfiguration::events).isTrue()
                prop(StepMonitoringConfiguration::meters).isTrue()
            }
        }

        val rowsFactory =
            previousStep.nextSteps[0].getProperty<suspend (ctx: StepContext<*, *>, input: Int) -> List<CassandraSaveRow>>(
                "rowsFactory"
            )
        assertThat(rowsFactory(relaxedMockk(), relaxedMockk())).hasSize(1)
        val columnsConfig =
            previousStep.nextSteps[0].getProperty<suspend (ctx: StepContext<*, *>, input: Int) -> List<String>>(
                "columnsConfig"
            )
        assertThat(columnsConfig(relaxedMockk(), relaxedMockk())).hasSize(1)
    }


    @Test
    fun `should add a complete configuration for the step`() = testDispatcherProvider.runTest {
        val previousStep = DummyStepSpecification()
        previousStep.cassandra().save {
            name = "my-save-step"
            connect {
                keyspace = "test_keyspace"
                datacenterProfile = DriverProfile.LOCAL
                datacenterName = "test_datacenter"
            }
            table { _, _ ->
                "test"
            }
            columns { _, _ ->
                listOf("col1", "col2", "col3", "col4")
            }
            rows { _, _ ->
                listOf(CassandraSaveRow("my-value1", true, null, 12.45),
                    CassandraSaveRow("my-value2", false, null, 100))
            }
            monitoring{
                events = true
                meters = true
            }
        }

        assertThat(previousStep.nextSteps[0]).isInstanceOf(CassandraSaveStepSpecificationImpl::class).all {
            prop("name") { CassandraSaveStepSpecificationImpl<*>::name.call(it) }.isEqualTo("my-save-step")
            prop(CassandraSaveStepSpecificationImpl<*>::serversConfig).all {
                prop(CassandraServerConfiguration::keyspace).isEqualTo("test_keyspace")
                prop(CassandraServerConfiguration::datacenterProfile).isEqualTo(DriverProfile.LOCAL)
                prop(CassandraServerConfiguration::datacenterName).isEqualTo("test_datacenter")
                prop(CassandraServerConfiguration::servers).all {
                    hasSize(1)
                    index(0).isEqualTo(DefaultValues.server)
                }
            }
            prop(CassandraSaveStepSpecificationImpl<*>::tableName).isNotNull()
            prop(CassandraSaveStepSpecificationImpl<*>::columnsConfig).isNotNull()
            prop(CassandraSaveStepSpecificationImpl<*>::rowsFactory).isNotNull()
            prop(CassandraSaveStepSpecificationImpl<*>::monitoringConfig).all {
                prop(StepMonitoringConfiguration::events).isTrue()
                prop(StepMonitoringConfiguration::meters).isTrue()
            }
        }

        val rowsFactory =
            previousStep.nextSteps[0].getProperty<suspend (ctx: StepContext<*, *>, input: Int) -> List<CassandraSaveRow>>(
                "rowsFactory"
            )

        assertThat(rowsFactory(relaxedMockk(), relaxedMockk())).hasSize(2)
        val columnsConfig =
            previousStep.nextSteps[0].getProperty<suspend (ctx: StepContext<*, *>, input: Int) -> List<String>>(
                "columnsConfig")
        assertThat(columnsConfig(relaxedMockk(), relaxedMockk())).hasSize(4)
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
        previousStep.cassandra().save {
            name = "my-save-step"
            table { _, _ -> "test" }
            columns { _, _ -> listOf("column") }
            rows { _, _ -> listOf(CassandraSaveRow()) }
        }

        assertThat(previousStep.nextSteps[0]).isInstanceOf(CassandraSaveStepSpecificationImpl::class).all {
            prop(CassandraSaveStepSpecificationImpl<*>::serversConfig).all {
                prop(CassandraServerConfiguration::servers).isEqualTo(listOf("default-host:9042", "default-host:9043"))
                prop(CassandraServerConfiguration::keyspace).isEqualTo("default_keyspace")
                prop(CassandraServerConfiguration::datacenterProfile).isEqualTo(DriverProfile.LOCAL)
                prop(CassandraServerConfiguration::datacenterName).isEqualTo("default_dc")
            }
            prop(CassandraSaveStepSpecificationImpl<*>::monitoringConfig).all {
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
        previousStep.cassandra().save {
            name = "my-save-step"
            connect {
                servers = listOf("override-host:9042")
            }
            monitoring {
                events = false
            }
            table { _, _ -> "test" }
            columns { _, _ -> listOf("column") }
            rows { _, _ -> listOf(CassandraSaveRow()) }
        }

        assertThat(previousStep.nextSteps[0]).isInstanceOf(CassandraSaveStepSpecificationImpl::class).all {
            prop(CassandraSaveStepSpecificationImpl<*>::serversConfig).all {
                prop(CassandraServerConfiguration::servers).isEqualTo(listOf("override-host:9042"))
                prop(CassandraServerConfiguration::keyspace).isEqualTo("default_keyspace")
                prop(CassandraServerConfiguration::datacenterProfile).isEqualTo(DriverProfile.LOCAL)
                prop(CassandraServerConfiguration::datacenterName).isEqualTo("default_dc")
            }
            prop(CassandraSaveStepSpecificationImpl<*>::monitoringConfig).all {
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
            .cassandra().save {
                name = "my-save-step"
                connect {
                    servers = listOf("override-host:9042")
                }
                monitoring {
                    events = false
                    // meters not set -> inherits true from scenario-level defaults
                }
                table { _, _ -> "test" }
                columns { _, _ -> listOf("column") }
                rows { _, _ -> listOf(CassandraSaveRow()) }
            }

        assertThat(previousStep.nextSteps[0]).isInstanceOf(CassandraSaveStepSpecificationImpl::class).all {
            prop(CassandraSaveStepSpecificationImpl<*>::serversConfig).all {
                prop(CassandraServerConfiguration::servers).isEqualTo(listOf("override-host:9042"))
                prop(CassandraServerConfiguration::keyspace).isEqualTo("step_keyspace")
                prop(CassandraServerConfiguration::datacenterProfile).isEqualTo(DriverProfile.LOCAL)
                prop(CassandraServerConfiguration::datacenterName).isEqualTo("default_dc")
            }
            prop(CassandraSaveStepSpecificationImpl<*>::monitoringConfig).all {
                prop(StepMonitoringConfiguration::events).isFalse()
                prop(StepMonitoringConfiguration::meters).isTrue()
            }
        }
    }
}
