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

package io.qalipsis.plugins.jakarta.producer

import assertk.all
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import io.mockk.spyk
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.steps.StepCreationContext
import io.qalipsis.api.steps.StepCreationContextImpl
import io.qalipsis.plugins.jakarta.destination.Queue
import io.qalipsis.plugins.jakarta.destination.Topic
import io.qalipsis.test.assertk.prop
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.relaxedMockk
import io.qalipsis.test.steps.AbstractStepSpecificationConverterTest
import jakarta.jms.Connection
import jakarta.jms.Session
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

/**
 * @author Krawist Ngoben
 */
@Suppress("UNCHECKED_CAST")
@OptIn(ExperimentalCoroutinesApi::class)
internal class JakartaProducerStepSpecificationConverterTest :
    AbstractStepSpecificationConverterTest<JakartaProducerStepSpecificationConverter>() {

    private val connectionFactory: () -> Connection = { relaxedMockk() }

    private val sessionFactory: (connection: Connection) -> Session = { relaxedMockk() }

    @Test
    override fun `should not support unexpected spec`() {
        Assertions.assertFalse(converter.support(relaxedMockk()))
    }

    @Test
    override fun `should support expected spec`() {
        Assertions.assertTrue(converter.support(relaxedMockk<JakartaProducerStepSpecificationImpl<*>>()))
    }

    @Test
    internal fun `should convert spec with name`() = testDispatcherProvider.runTest {
        val rec1 = JakartaProducerRecord(
            destination = Topic("topic-1"),
            value = "text-1"
        )
        val rec2 = JakartaProducerRecord(
            destination = Queue("queue-1"),
            value = "text-2"
        )

        val recordSupplier: (suspend (ctx: StepContext<*, *>, input: Any?) -> List<JakartaProducerRecord>) =
            { _, _ -> listOf(rec1, rec2) }

        val spec = JakartaProducerStepSpecificationImpl<Any>()
        spec.also {
            it.name = "my-step"
            it.connect(connectionFactory)
            it.session(sessionFactory)
            it.records(recordSupplier)
        }

        val creationContext = StepCreationContextImpl(scenarioSpecification, directedAcyclicGraph, spec)
        val spiedConverter = spyk(converter)

        // when
        runBlocking {
            spiedConverter.convert<Unit, Map<String, *>>(
                creationContext as StepCreationContext<JakartaProducerStepSpecificationImpl<*>>
            )
        }

        // then
        creationContext.createdStep!!.let {
            assertThat(it).isInstanceOf(JakartaProducerStep::class).all {
                prop("name").isEqualTo("my-step")
                prop("recordFactory").isEqualTo(recordSupplier)
                prop("jakartaProducer").isNotNull().all {
                    prop("connectionFactory").isEqualTo(connectionFactory)
                    prop("sessionFactory").isEqualTo(sessionFactory)
                }
                prop("retryPolicy").isNull()
            }
        }
    }

    companion object {
        @JvmField
        @RegisterExtension
        val testDispatcherProvider = TestDispatcherProvider()
    }
}
