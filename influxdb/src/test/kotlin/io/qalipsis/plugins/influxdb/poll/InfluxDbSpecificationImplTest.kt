/*
 * Copyright 2022 AERIS IT Solutions GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package io.qalipsis.plugins.influxdb.poll

import assertk.all
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isTrue
import assertk.assertions.prop
import io.qalipsis.api.scenario.StepSpecificationRegistry
import io.qalipsis.api.scenario.TestScenarioFactory
import io.qalipsis.api.scenario.scenario
import io.qalipsis.api.steps.SingletonConfiguration
import io.qalipsis.api.steps.SingletonType
import io.qalipsis.api.steps.StepMonitoringConfiguration
import io.qalipsis.plugins.influxdb.InfluxDbStepConnectionImpl
import io.qalipsis.plugins.influxdb.influxdb
import org.junit.jupiter.api.Test
import java.time.Duration

internal class InfluxDbSpecificationImplTest {

    @Test
    internal fun `should add minimal specification to the scenario with default values`() {
        val scenario = TestScenarioFactory.scenario("my-scenario") as StepSpecificationRegistry
        scenario.influxdb().poll {
            name = "my-step"
        }
        assertThat(scenario.rootSteps.first()).isInstanceOf(InfluxDbPollStepSpecificationImpl::class).all {
            prop(InfluxDbPollStepSpecificationImpl::name).isEqualTo("my-step")
            prop(InfluxDbPollStepSpecificationImpl::pollPeriod).isEqualTo(
                Duration.ofSeconds(10L)
            )
            prop(InfluxDbPollStepSpecificationImpl::monitoringConfiguration).all {
                prop(StepMonitoringConfiguration::events).isFalse()
                prop(StepMonitoringConfiguration::meters).isFalse()
            }
            prop(InfluxDbPollStepSpecificationImpl::singletonConfiguration).all {
                prop(SingletonConfiguration::type).isEqualTo(SingletonType.UNICAST)
                prop(SingletonConfiguration::bufferSize).isEqualTo(-1)
                prop(SingletonConfiguration::idleTimeout).isEqualTo(Duration.ZERO)
            }
        }
    }

    @Test
    internal fun `should add a complete specification to the scenario as broadcast whit monitoring`() {
        val scenario = TestScenarioFactory.scenario("my-scenario") as StepSpecificationRegistry
        scenario.influxdb().poll {
            name = "my-step"
            connect {
                server("http://127.0.0.1:8086", "DB", "org")
                basic("user", "pass")
            }
            pollDelay(Duration.ofSeconds(1L))
            monitoring {
                events = false
                meters = true
            }
            broadcast(123, Duration.ofSeconds(20))
        }
        assertThat(scenario.rootSteps.first()).isInstanceOf(InfluxDbPollStepSpecificationImpl::class).all {
            prop(InfluxDbPollStepSpecificationImpl::name).isEqualTo("my-step")

            prop(InfluxDbPollStepSpecificationImpl::pollPeriod).isEqualTo(Duration.ofSeconds(1L))
            prop(InfluxDbPollStepSpecificationImpl::monitoringConfiguration).all {
                prop(StepMonitoringConfiguration::events).isFalse()
                prop(StepMonitoringConfiguration::meters).isTrue()
            }
            prop(InfluxDbPollStepSpecificationImpl::connectionConfiguration).isInstanceOf(InfluxDbStepConnectionImpl::class)
                .all {
                    prop(InfluxDbStepConnectionImpl::bucket).isEqualTo("DB")
                    prop(InfluxDbStepConnectionImpl::user).isEqualTo("user")
                    prop(InfluxDbStepConnectionImpl::url).isEqualTo("http://127.0.0.1:8086")
                    prop(InfluxDbStepConnectionImpl::password).isEqualTo("pass")
                }
            prop(InfluxDbPollStepSpecificationImpl::singletonConfiguration).all {
                prop(SingletonConfiguration::type).isEqualTo(SingletonType.BROADCAST)
                prop(SingletonConfiguration::bufferSize).isEqualTo(123)
                prop(SingletonConfiguration::idleTimeout).isEqualTo(Duration.ofSeconds(20))
            }
        }
    }
}
