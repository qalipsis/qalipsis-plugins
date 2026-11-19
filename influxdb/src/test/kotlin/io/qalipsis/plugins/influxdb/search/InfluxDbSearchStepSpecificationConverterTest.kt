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

@file:Suppress("UNCHECKED_CAST")

package io.qalipsis.plugins.influxdb.search

import assertk.all
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isSameInstanceAs
import assertk.assertions.isTrue
import assertk.assertions.prop
import io.mockk.spyk
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.steps.StepCreationContext
import io.qalipsis.api.steps.StepCreationContextImpl
import io.qalipsis.test.assertk.prop
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.relaxedMockk
import io.qalipsis.test.steps.AbstractStepSpecificationConverterTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

/**
 *
 * @author Palina Bril
 */
@WithMockk
internal class InfluxDbSearchStepSpecificationConverterTest :
    AbstractStepSpecificationConverterTest<InfluxDbSearchStepSpecificationConverter>() {

    @JvmField
    @RegisterExtension
    val testDispatcherProvider = TestDispatcherProvider()

    private val queryFactory: (suspend (ctx: StepContext<*, *>, input: Any) -> String) = { _, _ -> "query" }

    @Test
    override fun `should not support unexpected spec`() {
        assertThat(converter.support(relaxedMockk()))
            .isFalse()
    }

    @Test
    override fun `should support expected spec`() {
        assertThat(converter.support(relaxedMockk<InfluxDbSearchStepSpecificationImpl<*>>()))
            .isTrue()
    }

    @Test
    fun `should convert with name and retry policy`() = testDispatcherProvider.runTest {
        // given
        val spec = InfluxDbSearchStepSpecificationImpl<Any>()
        spec.also {
            it.name = "influxdb-search-step"
            it.connect {
                server(
                    url = "http://localhost:8080",
                    bucket = "test",
                    org = "testtesttest"
                )
                basic(
                    password = "passpasspass",
                    user = "user"
                )
            }
            it.query(queryFactory)
            it.retryPolicy = mockedRetryPolicy
            it.monitoring {
                events = false
            }
        }
        val creationContext = StepCreationContextImpl(scenarioSpecification, directedAcyclicGraph, spec)
        val spiedConverter = spyk(converter, recordPrivateCalls = true)

        // when
        spiedConverter.convert<Unit, Map<String, *>>(
            creationContext as StepCreationContext<InfluxDbSearchStepSpecificationImpl<*>>
        )

        // then
        creationContext.createdStep!!.let { it ->
            assertThat(it).isInstanceOf(InfluxDbSearchStep::class).all {
                prop(InfluxDbSearchStep<*>::name).isEqualTo("influxdb-search-step")
                prop("influxDbQueryClient").all {
                    prop("eventsLogger").isNull()
                    prop("meterRegistry").isSameInstanceAs(meterRegistry)
                }
                prop("retryPolicy").isNotNull()
                prop("queryFactory").isSameInstanceAs(queryFactory)
            }
        }
    }

    @Test
    fun `should convert without name and retry policy`() = testDispatcherProvider.runTest {
        // given
        val spec = InfluxDbSearchStepSpecificationImpl<Any>()
        spec.also {
            it.connect {
                server(
                    url = "http://localhost:8080",
                    org = "testtesttest",
                    bucket = "test"
                )
                basic(
                    password = "passpasspass",
                    user = "user"
                )
            }
            it.query(queryFactory)
            it.monitoring {
                meters = false
            }
        }
        val creationContext = StepCreationContextImpl(scenarioSpecification, directedAcyclicGraph, spec)
        val spiedConverter = spyk(converter, recordPrivateCalls = true)

        // when
        spiedConverter.convert<Unit, Map<String, *>>(
            creationContext as StepCreationContext<InfluxDbSearchStepSpecificationImpl<*>>
        )

        // then
        creationContext.createdStep!!.let {
            assertThat(it).isInstanceOf(InfluxDbSearchStep::class).all {
                prop("name").isNotNull()
                prop("retryPolicy").isNull()
                prop("queryFactory").isSameInstanceAs(queryFactory)
                prop("influxDbQueryClient").all {
                    prop("meterRegistry").isNull()
                    prop("eventsLogger").isSameInstanceAs(eventsLogger)
                }
            }
        }
    }
}
