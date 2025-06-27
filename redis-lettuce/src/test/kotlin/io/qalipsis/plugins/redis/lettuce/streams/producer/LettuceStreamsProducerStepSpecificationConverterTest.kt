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

package io.qalipsis.plugins.redis.lettuce.streams.producer

import assertk.all
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isSameAs
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.spyk
import io.qalipsis.api.steps.StepCreationContext
import io.qalipsis.api.steps.StepCreationContextImpl
import io.qalipsis.plugins.redis.lettuce.configuration.RedisConnectionType
import io.qalipsis.test.assertk.prop
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.relaxedMockk
import io.qalipsis.test.steps.AbstractStepSpecificationConverterTest
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import kotlin.coroutines.CoroutineContext

@WithMockk
@Suppress("UNCHECKED_CAST")
internal class LettuceStreamsProducerStepSpecificationConverterTest :
    AbstractStepSpecificationConverterTest<LettuceStreamsProducerStepSpecificationConverter>() {

    @JvmField
    @RegisterExtension
    val testDispatcherProvider = TestDispatcherProvider()

    @RelaxedMockK
    private lateinit var ioCoroutineContext: CoroutineContext

    @Test
    override fun `should not support unexpected spec`() {
        Assertions.assertFalse(converter.support(relaxedMockk()))
    }

    @Test
    override fun `should support expected spec`() {
        Assertions.assertTrue(converter.support(relaxedMockk<LettuceStreamsProducerStepSpecificationImpl<*>>()))
    }


    @Test
    internal fun `should convert spec with name and retry policy`() = testDispatcherProvider.runTest {
        // given
        val spec = LettuceStreamsProducerStepSpecificationImpl<Any>()
        spec.apply {
            name = "my-step"
            retryPolicy = mockedRetryPolicy
            connection {
                nodes = listOf("localhost:6379")
                database = 1
                redisConnectionType = RedisConnectionType.CLUSTER
            }
            records { _, _ ->
                listOf()
            }
        }
        val creationContext = StepCreationContextImpl(scenarioSpecification, directedAcyclicGraph, spec)

        val spiedConverter = spyk(converter)

        // when
        spiedConverter.convert<Unit, Map<String, *>>(
            creationContext as StepCreationContext<LettuceStreamsProducerStepSpecificationImpl<*>>
        )


        // then
        creationContext.createdStep!!.let {
            assertThat(it).isInstanceOf(LettuceStreamsProducerStep::class).all {
                prop("name").isEqualTo("my-step")
                prop("meterRegistry").isNull()
                prop("ioCoroutineContext").isSameAs(ioCoroutineContext)
                prop("eventsLogger").isNull()
                prop("connectionFactory").isNotNull()
                prop("retryPolicy").isNotNull()
                prop("recordsFactory").isNotNull()
            }
        }
    }


    @Test
    internal fun `should convert spec without name and retry policy`() = testDispatcherProvider.runTest {
        // given
        val spec = LettuceStreamsProducerStepSpecificationImpl<Any>()
        spec.apply {
            connection {
                nodes = listOf("localhost:6379")
                database = 1
                redisConnectionType = RedisConnectionType.CLUSTER
            }
            records { _, _ ->
                listOf()
            }
            monitoring {
                events = true
                meters = true
            }
        }
        val creationContext = StepCreationContextImpl(scenarioSpecification, directedAcyclicGraph, spec)
        val spiedConverter = spyk(converter)

        // when
        spiedConverter.convert<Unit, Map<String, *>>(
            creationContext as StepCreationContext<LettuceStreamsProducerStepSpecificationImpl<*>>
        )

        // then
        creationContext.createdStep!!.let {
            assertThat(it).isInstanceOf(LettuceStreamsProducerStep::class).all {
                prop("name").isNotNull()
                prop("connectionFactory").isNotNull()
                prop("meterRegistry").isNotNull().isEqualTo(meterRegistry)
                prop("ioCoroutineContext").isSameAs(ioCoroutineContext)
                prop("eventsLogger").isNotNull().isEqualTo(eventsLogger)
                prop("retryPolicy").isNull()
                prop("recordsFactory").isNotNull()
            }
        }
    }
}