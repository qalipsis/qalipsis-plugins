package io.qalipsis.plugins.jms.consumer

import assertk.all
import assertk.assertThat
import assertk.assertions.containsOnly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isSameAs
import io.mockk.every
import io.mockk.spyk
import io.qalipsis.api.steps.StepCreationContext
import io.qalipsis.api.steps.StepCreationContextImpl
import io.qalipsis.api.steps.StepMonitoringConfiguration
import io.qalipsis.api.steps.datasource.DatasourceObjectConverter
import io.qalipsis.api.steps.datasource.IterativeDatasourceStep
import io.qalipsis.api.steps.datasource.processors.NoopDatasourceObjectProcessor
import io.qalipsis.plugins.jms.consumer.JmsScenario.queueConnection
import io.qalipsis.plugins.jms.deserializer.JmsStringDeserializer
import io.qalipsis.test.assertk.prop
import io.qalipsis.test.assertk.typedProp
import io.qalipsis.test.mockk.relaxedMockk
import io.qalipsis.test.steps.AbstractStepSpecificationConverterTest
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import javax.jms.Message
import javax.jms.QueueConnection
import javax.jms.TopicConnection
import io.aerisconsulting.catadioptre.invokeInvisible

/**
 *
 * @author Alexander Sosnovsky
 */
@Suppress("UNCHECKED_CAST")
internal class JmsConsumerStepSpecificationConverterTest :
    AbstractStepSpecificationConverterTest<JmsConsumerStepSpecificationConverter>() {

    private val queueConnectionFactory: () -> QueueConnection = { relaxedMockk() }

    private val topicConnectionFactory: () -> TopicConnection = { relaxedMockk() }

    @Test
    override fun `should not support unexpected spec`() {
        Assertions.assertFalse(converter.support(relaxedMockk()))
    }

    @Test
    override fun `should support expected spec`() {
        Assertions.assertTrue(converter.support(relaxedMockk<JmsConsumerStepSpecification<*>>()))
    }

    @Test
    internal fun `should convert spec with name and list of queues and generate an output`() {
        val deserializer = JmsStringDeserializer()
        val spec = JmsConsumerStepSpecification(deserializer)
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
                creationContext as StepCreationContext<JmsConsumerStepSpecification<*>>
            )
        }

        // then
        creationContext.createdStep!!.let {
            assertThat(it).isInstanceOf(IterativeDatasourceStep::class).all {
                prop("id").isEqualTo("my-step")
                prop("reader").isNotNull().isInstanceOf(JmsConsumerIterativeReader::class).all {
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
        val deserializer = JmsStringDeserializer()
        val spec = JmsConsumerStepSpecification(deserializer)
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
                creationContext as StepCreationContext<JmsConsumerStepSpecification<*>>
            )
        }

        // then
        creationContext.createdStep!!.let {
            assertThat(it).isInstanceOf(IterativeDatasourceStep::class).all {
                prop("id").isEqualTo("my-step")
                prop("reader").isNotNull().isInstanceOf(JmsConsumerIterativeReader::class).all {
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
        val deserializer = JmsStringDeserializer()

        // when
        val recordsConverter = converter.invokeInvisible<DatasourceObjectConverter<Message, out Any?>>("buildConverter", deserializer, monitoringConfiguration)

        // then
        assertThat(recordsConverter).isNotNull().isInstanceOf(JmsConsumerConverter::class).all {
            prop("consumedBytesCounter").isNull()
            prop("consumedRecordsCounter").isNull()
        }
    }

}
