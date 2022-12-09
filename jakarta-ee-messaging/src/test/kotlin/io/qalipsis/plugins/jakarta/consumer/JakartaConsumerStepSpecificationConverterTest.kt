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
import jakarta.jms.Message
import jakarta.jms.QueueConnection
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
        spec.apply {
            name = "my-step"
            queueConnection(queueConnectionFactory)
            queues("queue-1", "queue-2")
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
        spec.apply {
            name = "my-step"
            topicConnection(topicConnectionFactory)
            topics("topic-1", "topic-2")
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
