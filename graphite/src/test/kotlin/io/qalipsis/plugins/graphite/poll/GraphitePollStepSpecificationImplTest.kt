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

package io.qalipsis.plugins.graphite.poll

import assertk.all
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isSameInstanceAs
import assertk.assertions.isTrue
import assertk.assertions.prop
import io.mockk.mockk
import io.qalipsis.api.scenario.StepSpecificationRegistry
import io.qalipsis.api.scenario.TestScenarioFactory
import io.qalipsis.api.steps.SingletonConfiguration
import io.qalipsis.api.steps.SingletonType
import io.qalipsis.api.steps.StepMonitoringConfiguration
import io.qalipsis.plugins.graphite.GraphiteHttpConnectionSpecificationImpl
import io.qalipsis.plugins.graphite.graphite
import io.qalipsis.plugins.graphite.search.GraphiteQuery
import java.time.Duration
import org.junit.jupiter.api.Test

internal class GraphitePollStepSpecificationImplTest {

    @Test
    fun `should add minimal specification to the scenario with default values`() {
        val scenario = TestScenarioFactory.scenario("my-scenario") as StepSpecificationRegistry
        scenario.graphite().poll {
            name = "my-step"
        }
        assertThat(scenario.rootSteps.first()).isInstanceOf(GraphitePollStepSpecificationImpl::class).all {
            prop(GraphitePollStepSpecificationImpl::name).isEqualTo("my-step")
            prop(GraphitePollStepSpecificationImpl::pollPeriod).isEqualTo(
                Duration.ofSeconds(10L)
            )
            prop(GraphitePollStepSpecificationImpl::monitoringConfiguration).all {
                prop(StepMonitoringConfiguration::events).isTrue()
                prop(StepMonitoringConfiguration::meters).isTrue()
            }
            prop(GraphitePollStepSpecificationImpl::singletonConfiguration).all {
                prop(SingletonConfiguration::type).isEqualTo(SingletonType.UNICAST)
                prop(SingletonConfiguration::bufferSize).isEqualTo(-1)
                prop(SingletonConfiguration::idleTimeout).isEqualTo(Duration.ZERO)
            }
        }
    }

    @Test
    fun `should add a complete specification to the scenario as broadcast whit monitoring`() {
        val scenario = TestScenarioFactory.scenario("my-scenario") as StepSpecificationRegistry
        val queryBuilder = mockk<GraphiteQuery.() -> Unit>()
        scenario.graphite().poll {
            name = "my-step"
            connect {
                server("http://localhost:8086")
                basicAuthentication("myUser", "password")
            }
            pollDelay(Duration.ofSeconds(1L))
            monitoring {
                events = false
                meters = true
            }
            query(queryBuilder)
            broadcast(123, Duration.ofSeconds(20))
        }
        assertThat(scenario.rootSteps.first()).isInstanceOf(GraphitePollStepSpecificationImpl::class).all {
            prop(GraphitePollStepSpecificationImpl::name).isEqualTo("my-step")

            prop(GraphitePollStepSpecificationImpl::pollPeriod).isEqualTo(Duration.ofSeconds(1L))
            prop(GraphitePollStepSpecificationImpl::monitoringConfiguration).all {
                prop(StepMonitoringConfiguration::events).isFalse()
                prop(StepMonitoringConfiguration::meters).isTrue()
            }
            prop(GraphitePollStepSpecificationImpl::connectionConfiguration).isInstanceOf(
                GraphiteHttpConnectionSpecificationImpl::class
            )
                .all {
                    prop(GraphiteHttpConnectionSpecificationImpl::url).isEqualTo("http://localhost:8086")
                    prop(GraphiteHttpConnectionSpecificationImpl::username).isEqualTo("myUser")
                    prop(GraphiteHttpConnectionSpecificationImpl::password).isEqualTo("password")
                }
            prop(GraphitePollStepSpecificationImpl::singletonConfiguration).all {
                prop(SingletonConfiguration::type).isEqualTo(SingletonType.BROADCAST)
                prop(SingletonConfiguration::bufferSize).isEqualTo(123)
                prop(SingletonConfiguration::idleTimeout).isEqualTo(Duration.ofSeconds(20))
            }
            prop(GraphitePollStepSpecificationImpl::queryBuilder).isSameInstanceAs(queryBuilder)
        }
    }
}
