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

package io.qalipsis.plugins.influxdb.save

import assertk.all
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.isTrue
import assertk.assertions.prop
import com.influxdb.client.write.Point
import io.aerisconsulting.catadioptre.getProperty
import io.qalipsis.api.context.StepContext
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

internal class InfluxDbSaveStepSpecificationImplTest {

    @JvmField
    @RegisterExtension
    val testDispatcherProvider = TestDispatcherProvider()

    private val bucketName: (suspend (ctx: StepContext<*, *>, input: Any?) -> String) = { _, _ -> "test" }

    private val orgName: (suspend (ctx: StepContext<*, *>, input: Any) -> String) = { _, _ -> "testtesttest" }

    private val pointSupplier: (suspend (ctx: StepContext<*, *>, input: Any?) -> List<Point>) = { _, _ ->
        listOf(
            Point.measurement("temp").addTag("tag1", "first").addField("key1", "val1"),
            Point.measurement("temp").addTag("tag2", "second").addField("key2", "val2"),
            Point.measurement("temp").addTag("tag3", "third").addField("key3", "val3")
        )
    }

    @Test
    fun `should add minimal configuration for the step`() = testDispatcherProvider.runTest {
        val previousStep = DummyStepSpecification()
        previousStep.influxdb().save {
            name = "my-save-step"
            connect {
                server(
                    url = "http://localhost:8080",
                    bucket = "test",
                    org = "testtesttest"
                )
                basic(
                    user = "user",
                    password = "passpasspass"
                )
            }
            query {
                bucket = bucketName
                organization = orgName
                points = pointSupplier
            }
        }

        assertThat(previousStep.nextSteps[0]).isInstanceOf(InfluxDbSaveStepSpecificationImpl::class).all {
            prop("name") { InfluxDbSaveStepSpecificationImpl<*>::name.call(it) }.isEqualTo("my-save-step")
            prop(InfluxDbSaveStepSpecificationImpl<*>::clientBuilder).isNotNull()
            prop(InfluxDbSaveStepSpecificationImpl<*>::queryConfiguration).all {
                prop(InfluxDbSavePointConfiguration<*>::points).isEqualTo(pointSupplier)
            }
            prop(InfluxDbSaveStepSpecificationImpl<*>::monitoringConfig).isNotNull().all {
                prop(StepMonitoringConfiguration::events).isFalse()
                prop(StepMonitoringConfiguration::meters).isFalse()
            }
        }

        val step: InfluxDbSaveStepSpecificationImpl<*> =
            previousStep.nextSteps[0] as InfluxDbSaveStepSpecificationImpl<*>

        val bucket =
            step.queryConfiguration.getProperty<suspend (ctx: StepContext<*, *>, input: Int) -> String>("bucket")
        assertThat(bucket(relaxedMockk(), relaxedMockk())).isEqualTo("test")

        val org =
            step.queryConfiguration.getProperty<suspend (ctx: StepContext<*, *>, input: Int) -> String>("organization")
        assertThat(org(relaxedMockk(), relaxedMockk())).isEqualTo("testtesttest")
    }

    @Test
    fun `should add a complete configuration for the step`() = testDispatcherProvider.runTest {
        val previousStep = DummyStepSpecification()
        previousStep.influxdb().save {
            name = "my-save-step"
            connect {
                server(
                    url = "http://localhost:8080",
                    bucket = "test",
                    org = "testtesttest"
                )
                basic(
                    user = "user",
                    password = "passpasspass"
                )
            }
            query {
                bucket = bucketName
                organization = orgName
                points = pointSupplier
            }
            monitoring {
                events = true
            }
        }

        assertThat(previousStep.nextSteps[0]).isInstanceOf(InfluxDbSaveStepSpecificationImpl::class).all {
            prop("name") { InfluxDbSaveStepSpecificationImpl<*>::name.call(it) }.isEqualTo("my-save-step")
            prop(InfluxDbSaveStepSpecificationImpl<*>::clientBuilder).isNotNull()
            prop(InfluxDbSaveStepSpecificationImpl<*>::queryConfiguration).all {
                prop(InfluxDbSavePointConfiguration<*>::points).isEqualTo(pointSupplier)
            }
            prop(InfluxDbSaveStepSpecificationImpl<*>::monitoringConfig).all {
                prop(StepMonitoringConfiguration::events).isTrue()
                prop(StepMonitoringConfiguration::meters).isFalse()
            }
        }

        val step: InfluxDbSaveStepSpecificationImpl<*> =
            previousStep.nextSteps[0] as InfluxDbSaveStepSpecificationImpl<*>

        val bucket =
            step.queryConfiguration.getProperty<suspend (ctx: StepContext<*, *>, input: Int) -> String>("bucket")
        assertThat(bucket(relaxedMockk(), relaxedMockk())).isEqualTo("test")

        val org =
            step.queryConfiguration.getProperty<suspend (ctx: StepContext<*, *>, input: Int) -> String>("organization")
        assertThat(org(relaxedMockk(), relaxedMockk())).isEqualTo("testtesttest")
    }

    @Test
    fun `should apply defaults from InfluxDbDefaultsExtension`() = testDispatcherProvider.runTest {
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
        previousStep.influxdb().save {
            name = "my-save-step"
            query {
                bucket = bucketName
                organization = orgName
                points = pointSupplier
            }
        }

        assertThat(previousStep.nextSteps[0]).isInstanceOf(InfluxDbSaveStepSpecificationImpl::class).all {
            prop(InfluxDbSaveStepSpecificationImpl<*>::connectionConfig).all {
                prop(InfluxDbStepConnectionImpl::url).isEqualTo("http://default-server:8086")
                prop(InfluxDbStepConnectionImpl::bucket).isEqualTo("default-bucket")
                prop(InfluxDbStepConnectionImpl::org).isEqualTo("default-org")
                prop(InfluxDbStepConnectionImpl::user).isEqualTo("default-user")
                prop(InfluxDbStepConnectionImpl::password).isEqualTo("default-password")
            }
            prop(InfluxDbSaveStepSpecificationImpl<*>::monitoringConfig).all {
                prop(StepMonitoringConfiguration::events).isTrue()
                prop(StepMonitoringConfiguration::meters).isTrue()
            }
        }
    }

    @Test
    fun `should allow overriding defaults from InfluxDbDefaultsExtension`() = testDispatcherProvider.runTest {
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
        previousStep.influxdb().save {
            name = "my-save-step"
            connect {
                server("http://override-server:8086", "override-bucket", "override-org")
            }
            monitoring {
                events = false
            }
            query {
                bucket = bucketName
                organization = orgName
                points = pointSupplier
            }
        }

        assertThat(previousStep.nextSteps[0]).isInstanceOf(InfluxDbSaveStepSpecificationImpl::class).all {
            prop(InfluxDbSaveStepSpecificationImpl<*>::connectionConfig).all {
                prop(InfluxDbStepConnectionImpl::url).isEqualTo("http://override-server:8086")
                prop(InfluxDbStepConnectionImpl::bucket).isEqualTo("override-bucket")
                prop(InfluxDbStepConnectionImpl::org).isEqualTo("override-org")
                prop(InfluxDbStepConnectionImpl::user).isEqualTo("default-user")
                prop(InfluxDbStepConnectionImpl::password).isEqualTo("default-password")
            }
            prop(InfluxDbSaveStepSpecificationImpl<*>::monitoringConfig).all {
                prop(StepMonitoringConfiguration::events).isFalse()
                prop(StepMonitoringConfiguration::meters).isTrue()
            }
        }
    }

    @Test
    fun `should allow successive overwriting of defaults`() = testDispatcherProvider.runTest {
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
            .influxdb().save {
                connect {
                    server("http://override-server:8086", "override-bucket", "override-org")
                }
                monitoring {
                    events = false
                }
                query {
                    bucket = bucketName
                    organization = orgName
                    points = pointSupplier
                }
            }

        assertThat(previousStep.nextSteps[0]).isInstanceOf(InfluxDbSaveStepSpecificationImpl::class).all {
            prop(InfluxDbSaveStepSpecificationImpl<*>::connectionConfig).all {
                prop(InfluxDbStepConnectionImpl::url).isEqualTo("http://override-server:8086")
                prop(InfluxDbStepConnectionImpl::bucket).isEqualTo("override-bucket")
                prop(InfluxDbStepConnectionImpl::org).isEqualTo("override-org")
                prop(InfluxDbStepConnectionImpl::user).isEqualTo("default-user")
                prop(InfluxDbStepConnectionImpl::password).isEqualTo("default-password")
            }
            prop(InfluxDbSaveStepSpecificationImpl<*>::monitoringConfig).all {
                prop(StepMonitoringConfiguration::events).isFalse()
                prop(StepMonitoringConfiguration::meters).isTrue()
            }
        }
    }
}
