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

package io.qalipsis.plugins.graphite.poll

import assertk.all
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isSameAs
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
import org.junit.jupiter.api.Test
import java.time.Duration

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
                prop(StepMonitoringConfiguration::events).isFalse()
                prop(StepMonitoringConfiguration::meters).isFalse()
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
            prop(GraphitePollStepSpecificationImpl::queryBuilder).isSameAs(queryBuilder)
        }
    }
}
