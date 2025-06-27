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

package io.qalipsis.plugins.jms.producer

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
import io.qalipsis.test.assertk.prop
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.relaxedMockk
import io.qalipsis.test.steps.AbstractStepSpecificationConverterTest
import kotlinx.coroutines.runBlocking
import org.apache.activemq.command.ActiveMQTopic
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import javax.jms.Connection

/**
 *
 * @author Alexander Sosnovsky
 */
@Suppress("UNCHECKED_CAST")
internal class JmsProducerStepSpecificationConverterTest :
    AbstractStepSpecificationConverterTest<JmsProducerStepSpecificationConverter>() {

    @JvmField
    @RegisterExtension
    val testDispatcherProvider = TestDispatcherProvider()

    private val connectionFactory: () -> Connection = { relaxedMockk() }

    @Test
    override fun `should not support unexpected spec`() {
        Assertions.assertFalse(converter.support(relaxedMockk()))
    }

    @Test
    override fun `should support expected spec`() {
        Assertions.assertTrue(converter.support(relaxedMockk<JmsProducerStepSpecificationImpl<*>>()))
    }

    @Test
    internal fun `should convert spec with name`() = testDispatcherProvider.runTest {
        val rec1 = JmsProducerRecord(
            destination = ActiveMQTopic().createDestination("dest-1"),
            value = "text-1"
        )
        val rec2 = JmsProducerRecord(
            destination = ActiveMQTopic().createDestination("dest-2"),
            value = "text-2"
        )

        val recordSupplier: (suspend (ctx: StepContext<*, *>, input: Any?) -> List<JmsProducerRecord>) =
            { _, _ -> listOf(rec1, rec2) }

        val spec = JmsProducerStepSpecificationImpl<Any>()
        spec.also {
            it.name = "my-step"
            it.connect(connectionFactory)
            it.records(recordSupplier)
        }

        val creationContext = StepCreationContextImpl(scenarioSpecification, directedAcyclicGraph, spec)
        val spiedConverter = spyk(converter)

        // when
        runBlocking {
            spiedConverter.convert<Unit, Map<String, *>>(
                creationContext as StepCreationContext<JmsProducerStepSpecificationImpl<*>>
            )
        }

        // then
        creationContext.createdStep!!.let {
            assertThat(it).isInstanceOf(JmsProducerStep::class).all {
                prop("name").isEqualTo("my-step")
                prop("recordFactory").isEqualTo(recordSupplier)
                prop("jmsProducer").isNotNull().all {
                    prop("connectionFactory").isEqualTo(connectionFactory)
                }
                prop("retryPolicy").isNull()
            }
        }
    }
}
