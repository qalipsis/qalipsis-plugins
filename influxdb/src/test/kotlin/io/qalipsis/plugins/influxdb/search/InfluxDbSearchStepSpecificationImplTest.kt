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

package io.qalipsis.plugins.influxdb.search

import assertk.all
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.isTrue
import assertk.assertions.prop
import io.qalipsis.api.scenario.StepSpecificationRegistry
import io.qalipsis.api.scenario.TestScenarioFactory
import io.qalipsis.api.steps.DummyStepSpecification
import io.qalipsis.api.steps.StepMonitoringConfiguration
import io.qalipsis.plugins.influxdb.InfluxDbStepConnectionImpl
import io.qalipsis.plugins.influxdb.configuration.defaults
import io.qalipsis.plugins.influxdb.influxdb
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.relaxedMockk
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

/**
 *
 * @author Palina Bril
 */
internal class InfluxDbSearchStepSpecificationImplTest {

    @JvmField
    @RegisterExtension
    val testDispatcherProvider = TestDispatcherProvider()

    @Test
    fun `should add minimal configuration for the step`() = testDispatcherProvider.run {
        // given
        val previousStep = DummyStepSpecification()

        // when
        previousStep.influxdb().search {
            name = "my-search-step"
            connect {
                server(
                    url = "http://localhost:8080",
                    org = "testtesttest",
                    bucket = "test"

                )
                basic(
                    user = "user",
                    password = "passpasspass"
                )
            }
            query { _, _ -> "the query" }
        }

        // then
        assertThat(previousStep.nextSteps[0]).isInstanceOf(InfluxDbSearchStepSpecificationImpl::class).all {
            prop("name") { InfluxDbSearchStepSpecificationImpl<Int>::name.call(it) }.isEqualTo("my-search-step")
            prop(InfluxDbSearchStepSpecificationImpl<*>::queryFactory).isNotNull()
            prop(InfluxDbSearchStepSpecificationImpl<*>::monitoringConfig).isNotNull().all {
                prop(StepMonitoringConfiguration::events).isTrue()
                prop(StepMonitoringConfiguration::meters).isTrue()
            }
        }

        @Suppress("UNCHECKED_CAST") val step = previousStep.nextSteps[0] as InfluxDbSearchStepSpecificationImpl<Int>
        assertThat(step.queryFactory(relaxedMockk(), relaxedMockk())).isEqualTo("the query")
    }


    @Test
    fun `should add a complete configuration for the step`() = testDispatcherProvider.run {
        // given
        val previousStep = DummyStepSpecification()

        // when
        previousStep.influxdb().search {
            name = "my-search-step"
            connect {
                server(
                    url = "http://localhost:8080",
                    org = "testtesttest",
                    bucket = "test"
                )
                basic(
                    user = "user",
                    password = "passpasspass"
                )
            }
            query { _, _ -> "the query" }

            monitoring {
                events = true
                meters = false
            }

        }

        // then
        assertThat(previousStep.nextSteps[0]).isInstanceOf(InfluxDbSearchStepSpecificationImpl::class).all {
            prop("name") { InfluxDbSearchStepSpecificationImpl<*>::name.call(it) }.isEqualTo("my-search-step")
            prop(InfluxDbSearchStepSpecificationImpl<*>::queryFactory).isNotNull()
            prop(InfluxDbSearchStepSpecificationImpl<*>::monitoringConfig).all {
                prop(StepMonitoringConfiguration::events).isTrue()
                prop(StepMonitoringConfiguration::meters).isFalse()
            }
        }

        @Suppress("UNCHECKED_CAST") val step = previousStep.nextSteps[0] as InfluxDbSearchStepSpecificationImpl<Int>
        assertThat(step.queryFactory(relaxedMockk(), relaxedMockk())).isEqualTo("the query")
    }

    @Test
    fun `should apply defaults from InfluxDbDefaultsExtension`() = testDispatcherProvider.run {
        val scenario = TestScenarioFactory.scenario("my-scenario", {
            influxdb().defaults {
                connect {
                    server("http://default-server:8086", "default-bucket", "default-org")
                    basic("default-user", "default-password")
                }
                monitoring {
                    events = true
                    meters = true
                }
            }
        }) as StepSpecificationRegistry

        val previousStep = DummyStepSpecification()
        previousStep.scenario = scenario
        previousStep.influxdb().search {
            name = "my-search-step"
            query { _, _ -> "the query" }
        }

        assertThat(previousStep.nextSteps[0]).isInstanceOf(InfluxDbSearchStepSpecificationImpl::class).all {
            prop(InfluxDbSearchStepSpecificationImpl<*>::connectionConfig).all {
                prop(InfluxDbStepConnectionImpl::url).isEqualTo("http://default-server:8086")
                prop(InfluxDbStepConnectionImpl::bucket).isEqualTo("default-bucket")
                prop(InfluxDbStepConnectionImpl::org).isEqualTo("default-org")
                prop(InfluxDbStepConnectionImpl::user).isEqualTo("default-user")
                prop(InfluxDbStepConnectionImpl::password).isEqualTo("default-password")
            }
            prop(InfluxDbSearchStepSpecificationImpl<*>::monitoringConfig).all {
                prop(StepMonitoringConfiguration::events).isTrue()
                prop(StepMonitoringConfiguration::meters).isTrue()
            }
        }
    }

    @Test
    fun `should allow overriding defaults from InfluxDbDefaultsExtension`() = testDispatcherProvider.run {
        val scenario = TestScenarioFactory.scenario("my-scenario", {
            influxdb().defaults {
                connect {
                    server("http://default-server:8086", "default-bucket", "default-org")
                    basic("default-user", "default-password")
                }
                monitoring {
                    events = true
                    meters = true
                }
            }
        }) as StepSpecificationRegistry

        val previousStep = DummyStepSpecification()
        previousStep.scenario = scenario
        previousStep.influxdb().search {
            name = "my-search-step"
            connect {
                server("http://override-server:8086", "override-bucket", "override-org")
            }
            monitoring {
                events = false
            }
            query { _, _ -> "the query" }
        }

        assertThat(previousStep.nextSteps[0]).isInstanceOf(InfluxDbSearchStepSpecificationImpl::class).all {
            prop(InfluxDbSearchStepSpecificationImpl<*>::connectionConfig).all {
                prop(InfluxDbStepConnectionImpl::url).isEqualTo("http://override-server:8086")
                prop(InfluxDbStepConnectionImpl::bucket).isEqualTo("override-bucket")
                prop(InfluxDbStepConnectionImpl::org).isEqualTo("override-org")
                prop(InfluxDbStepConnectionImpl::user).isEqualTo("default-user")
                prop(InfluxDbStepConnectionImpl::password).isEqualTo("default-password")
            }
            prop(InfluxDbSearchStepSpecificationImpl<*>::monitoringConfig).all {
                prop(StepMonitoringConfiguration::events).isFalse()
                prop(StepMonitoringConfiguration::meters).isTrue()
            }
        }
    }

    @Test
    fun `should allow successive overwriting of defaults`() = testDispatcherProvider.run {
        val scenario = TestScenarioFactory.scenario("my-scenario", {
            influxdb().defaults {
                connect {
                    server("http://default-server:8086", "default-bucket", "default-org")
                    basic("default-user", "default-password")
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
            .influxdb().defaults {
                connect {
                    server("http://step-default-server:8086", "step-bucket", "step-org")
                }
            }
            .influxdb().search {
                connect {
                    server("http://override-server:8086", "override-bucket", "override-org")
                }
                monitoring {
                    events = false
                }
                query { _, _ -> "the query" }
            }

        assertThat(previousStep.nextSteps[0]).isInstanceOf(InfluxDbSearchStepSpecificationImpl::class).all {
            prop(InfluxDbSearchStepSpecificationImpl<*>::connectionConfig).all {
                prop(InfluxDbStepConnectionImpl::url).isEqualTo("http://override-server:8086")
                prop(InfluxDbStepConnectionImpl::bucket).isEqualTo("override-bucket")
                prop(InfluxDbStepConnectionImpl::org).isEqualTo("override-org")
                prop(InfluxDbStepConnectionImpl::user).isEqualTo("default-user")
                prop(InfluxDbStepConnectionImpl::password).isEqualTo("default-password")
            }
            prop(InfluxDbSearchStepSpecificationImpl<*>::monitoringConfig).all {
                prop(StepMonitoringConfiguration::events).isFalse()
                prop(StepMonitoringConfiguration::meters).isTrue()
            }
        }
    }
}
