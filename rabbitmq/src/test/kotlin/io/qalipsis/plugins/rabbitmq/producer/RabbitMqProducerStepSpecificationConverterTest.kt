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

package io.qalipsis.plugins.rabbitmq.producer

import assertk.all
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import com.rabbitmq.client.ConnectionFactory
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.spyk
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.steps.StepCreationContext
import io.qalipsis.api.steps.StepCreationContextImpl
import io.qalipsis.test.assertk.prop
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.relaxedMockk
import io.qalipsis.test.steps.AbstractStepSpecificationConverterTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

/**
 *
 * @author Alexander Sosnovsky
 */
@ExperimentalCoroutinesApi
@Suppress("UNCHECKED_CAST")
@WithMockk
internal class RabbitMqProducerStepSpecificationConverterTest :
    AbstractStepSpecificationConverterTest<RabbitMqProducerStepSpecificationConverter>() {

    @RegisterExtension
    val testDispatcherProvider = TestDispatcherProvider()

    @RelaxedMockK
    private lateinit var connectionFactory: ConnectionFactory

    @Test
    override fun `should not support unexpected spec`() {
        Assertions.assertFalse(converter.support(relaxedMockk()))
    }

    @Test
    override fun `should support expected spec`() {
        Assertions.assertTrue(converter.support(relaxedMockk<RabbitMqProducerStepSpecificationImpl<*>>()))
    }

    @Test
    internal fun `should convert spec with name`() = testDispatcherProvider.runTest {
        val rec1 = RabbitMqProducerRecord(
            exchange = "dest-1",
            routingKey = "key-1",
            props = null,
            value = "text-1".toByteArray()
        )
        val rec2 = RabbitMqProducerRecord(
            exchange = "dest-2",
            routingKey = "key-2",
            props = null,
            value = "text-2".toByteArray()
        )

        val recordSupplier: (suspend (ctx: StepContext<*, *>, input: Any?) -> List<RabbitMqProducerRecord>) =
            { _, _ -> listOf(rec1, rec2) }

        val spec = RabbitMqProducerStepSpecificationImpl<Any>()
        spec.also {
            it.name = "my-step"
            it.connection {
                host = "localhost"
            }
            it.concurrency(2)
            it.records(recordSupplier)
        }

        val creationContext = StepCreationContextImpl(scenarioSpecification, directedAcyclicGraph, spec)
        val spiedConverter = spyk(converter)


        every { spiedConverter["buildConnectionFactory"](refEq(spec.connectionConfiguration)) } returns connectionFactory

        // when
        testDispatcherProvider.run {
            spiedConverter.convert<Unit, Map<String, *>>(
                creationContext as StepCreationContext<RabbitMqProducerStepSpecificationImpl<*>>
            )
        }

        // then
        creationContext.createdStep!!.let {
            assertThat(it).isInstanceOf(RabbitMqProducerStep::class).all {
                prop("name").isEqualTo("my-step")
                prop("recordFactory").isEqualTo(recordSupplier)
                prop("rabbitMqProducer").isNotNull().all {
                    prop("connectionFactory").isEqualTo(connectionFactory)
                }
                prop("retryPolicy").isNull()
            }
        }
    }
}
