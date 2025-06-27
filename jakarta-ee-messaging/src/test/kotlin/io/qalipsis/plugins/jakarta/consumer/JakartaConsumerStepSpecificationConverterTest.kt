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

package io.qalipsis.plugins.jakarta.consumer

import assertk.all
import assertk.assertThat
import assertk.assertions.containsOnly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isSameAs
import io.aerisconsulting.catadioptre.invokeInvisible
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import io.qalipsis.api.steps.StepCreationContext
import io.qalipsis.api.steps.StepCreationContextImpl
import io.qalipsis.api.steps.StepMonitoringConfiguration
import io.qalipsis.api.steps.datasource.DatasourceObjectConverter
import io.qalipsis.api.steps.datasource.IterativeDatasourceStep
import io.qalipsis.api.steps.datasource.processors.NoopDatasourceObjectProcessor
import io.qalipsis.plugins.jakarta.deserializer.JakartaStringDeserializer
import io.qalipsis.test.assertk.prop
import io.qalipsis.test.assertk.typedProp
import io.qalipsis.test.mockk.relaxedMockk
import io.qalipsis.test.steps.AbstractStepSpecificationConverterTest
import jakarta.jms.Connection
import jakarta.jms.Message
import jakarta.jms.QueueConnection
import jakarta.jms.Session
import jakarta.jms.TopicConnection
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

/**
 * @author Krawist Ngoben
 */
@Suppress("UNCHECKED_CAST")
internal class JakartaConsumerStepSpecificationConverterTest :
    AbstractStepSpecificationConverterTest<JakartaConsumerStepSpecificationConverter>() {

    private val queueConnectionFactory: () -> QueueConnection = { relaxedMockk() }

    private val topicConnectionFactory: () -> TopicConnection = { relaxedMockk() }

    @Test
    override fun `should not support unexpected spec`() {
        Assertions.assertFalse(converter.support(relaxedMockk()))
    }

    @Test
    override fun `should support expected spec`() {
        Assertions.assertTrue(converter.support(relaxedMockk<JakartaConsumerStepSpecification<*>>()))
    }

    @Test
    internal fun `should convert spec with name and list of queues and generate an output`() {
        val deserializer = JakartaStringDeserializer()
        val spec = JakartaConsumerStepSpecification(deserializer)
        val sessionFactory: ((Connection) -> Session) = { mockk() }
        spec.apply {
            name = "my-step"
            queueConnection(queueConnectionFactory)
            queues("queue-1", "queue-2")
            session(sessionFactory)
        }
        val creationContext = StepCreationContextImpl(scenarioSpecification, directedAcyclicGraph, spec)
        val spiedConverter = spyk(converter, recordPrivateCalls = true)
        val recordsConverter: DatasourceObjectConverter<Message, out Any?> =
            relaxedMockk()
        every {
            spiedConverter["buildConverter"](
                refEq(spec.valueDeserializer),
                refEq(spec.monitoringConfig)
            )
        } returns recordsConverter

        // when
        runBlocking {
            spiedConverter.convert<Unit, Map<String, *>>(
                creationContext as StepCreationContext<JakartaConsumerStepSpecification<*>>
            )
        }

        // then
        creationContext.createdStep!!.let {
            assertThat(it).isInstanceOf(IterativeDatasourceStep::class).all {
                prop("name").isEqualTo("my-step")
                prop("reader").isNotNull().isInstanceOf(JakartaConsumerIterativeReader::class).all {
                    prop("stepId").isEqualTo("my-step")
                    prop("topicConnectionFactory").isNull()
                    prop("queueConnectionFactory").isSameAs(queueConnectionFactory)
                    prop("sessionFactory").isSameAs(sessionFactory)
                    typedProp<Collection<String>>("queues").containsOnly("queue-1", "queue-2")
                    typedProp<Collection<String>>("topics").isEmpty()
                }
                prop("processor").isNotNull().isInstanceOf(NoopDatasourceObjectProcessor::class)
                prop("converter").isNotNull().isSameAs(recordsConverter)
            }
        }
    }

    @Test
    internal fun `should convert spec with name and list of topics and generate an output`() {
        val deserializer = JakartaStringDeserializer()
        val spec = JakartaConsumerStepSpecification(deserializer)
        val sessionFactory: ((Connection) -> Session) = { mockk() }
        spec.apply {
            name = "my-step"
            topicConnection(topicConnectionFactory)
            topics("topic-1", "topic-2")
            session(sessionFactory)
        }
        val creationContext = StepCreationContextImpl(scenarioSpecification, directedAcyclicGraph, spec)
        val spiedConverter = spyk(converter, recordPrivateCalls = true)
        val recordsConverter: DatasourceObjectConverter<Message, out Any?> =
            relaxedMockk()
        every {
            spiedConverter["buildConverter"](
                refEq(spec.valueDeserializer),
                refEq(spec.monitoringConfig)
            )
        } returns recordsConverter

        // when
        runBlocking {
            spiedConverter.convert<Unit, Map<String, *>>(
                creationContext as StepCreationContext<JakartaConsumerStepSpecification<*>>
            )
        }

        // then
        creationContext.createdStep!!.let {
            assertThat(it).isInstanceOf(IterativeDatasourceStep::class).all {
                prop("name").isEqualTo("my-step")
                prop("reader").isNotNull().isInstanceOf(JakartaConsumerIterativeReader::class).all {
                    prop("stepId").isEqualTo("my-step")
                    prop("topicConnectionFactory").isSameAs(topicConnectionFactory)
                    prop("sessionFactory").isSameAs(sessionFactory)
                    prop("queueConnectionFactory").isNull()
                    typedProp<Collection<String>>("topics").containsOnly("topic-1", "topic-2")
                    typedProp<Collection<String>>("queues").isEmpty()
                }
                prop("processor").isNotNull().isInstanceOf(NoopDatasourceObjectProcessor::class)
                prop("converter").isNotNull().isSameAs(recordsConverter)
            }
        }
    }


    @Test
    internal fun `should build converter`() {
        // given
        val monitoringConfiguration = StepMonitoringConfiguration()
        val deserializer = JakartaStringDeserializer()

        // when
        val recordsConverter = converter.invokeInvisible<DatasourceObjectConverter<Message, out Any?>>(
            "buildConverter",
            deserializer,
            monitoringConfiguration
        )

        // then
        assertThat(recordsConverter).isNotNull().isInstanceOf(JakartaConsumerConverter::class).all {
            prop("consumedBytesCounter").isNull()
            prop("consumedRecordsCounter").isNull()
        }
    }

}
