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

package io.qalipsis.plugins.influxdb.search

import assertk.all
import assertk.assertThat
import assertk.assertions.*
import io.aerisconsulting.catadioptre.getProperty
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.steps.DummyStepSpecification
import io.qalipsis.api.steps.StepMonitoringConfiguration
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
            search {
                query = { _, _ -> "query" }
            }
        }

        // then
        assertThat(previousStep.nextSteps[0]).isInstanceOf(InfluxDbSearchStepSpecificationImpl::class).all {
            prop("name") { InfluxDbSearchStepSpecificationImpl<*>::name.call(it) }.isEqualTo("my-search-step")
            prop(InfluxDbSearchStepSpecificationImpl<*>::searchConfig).isNotNull().all {
                prop(InfluxDbQueryConfiguration<*>::query).isNotNull()
            }
            prop(InfluxDbSearchStepSpecificationImpl<*>::monitoringConfig).isNotNull().all {
                prop(StepMonitoringConfiguration::events).isFalse()
                prop(StepMonitoringConfiguration::meters).isFalse()
            }
        }

        val step: InfluxDbSearchStepSpecificationImpl<*> =
            previousStep.nextSteps[0] as InfluxDbSearchStepSpecificationImpl<*>

        val query = step.searchConfig.getProperty<suspend (ctx: StepContext<*, *>, input: Int) -> String>("query")
        assertThat(query(relaxedMockk(), relaxedMockk())).isEqualTo("query")
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
            search {
                query = { _, _ -> "query" }
            }

            monitoring {
                events = true
                meters = false
            }

        }

        // then
        assertThat(previousStep.nextSteps[0]).isInstanceOf(InfluxDbSearchStepSpecificationImpl::class).all {
            prop("name") { InfluxDbSearchStepSpecificationImpl<*>::name.call(it) }.isEqualTo("my-search-step")
            prop(InfluxDbSearchStepSpecificationImpl<*>::searchConfig).isNotNull().all {
                prop(InfluxDbQueryConfiguration<*>::query).isNotNull()
            }
            prop(InfluxDbSearchStepSpecificationImpl<*>::monitoringConfig).all {
                prop(StepMonitoringConfiguration::events).isTrue()
                prop(StepMonitoringConfiguration::meters).isFalse()
            }
        }

        val step: InfluxDbSearchStepSpecificationImpl<*> =
            previousStep.nextSteps[0] as InfluxDbSearchStepSpecificationImpl<*>

        val query = step.searchConfig.getProperty<suspend (ctx: StepContext<*, *>, input: Int) -> String>("query")
        assertThat(query(relaxedMockk(), relaxedMockk())).isEqualTo("query")
    }
}
