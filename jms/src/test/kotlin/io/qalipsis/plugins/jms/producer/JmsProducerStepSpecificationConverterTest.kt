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
