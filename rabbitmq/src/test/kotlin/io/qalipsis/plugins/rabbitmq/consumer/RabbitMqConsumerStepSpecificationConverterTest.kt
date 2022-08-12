package io.qalipsis.plugins.rabbitmq.consumer

import assertk.all
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isSameAs
import com.rabbitmq.client.ConnectionFactory
import com.rabbitmq.client.Delivery
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.slot
import io.mockk.spyk
import io.qalipsis.api.messaging.deserializer.MessageStringDeserializer
import io.qalipsis.api.steps.StepCreationContext
import io.qalipsis.api.steps.StepCreationContextImpl
import io.qalipsis.api.steps.datasource.DatasourceObjectConverter
import io.qalipsis.api.steps.datasource.IterativeDatasourceStep
import io.qalipsis.api.steps.datasource.processors.NoopDatasourceObjectProcessor
import io.qalipsis.test.assertk.prop
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.relaxedMockk
import io.qalipsis.test.steps.AbstractStepSpecificationConverterTest
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

/**
 * @author Gabriel Moraes
 */
@Suppress("UNCHECKED_CAST")
@WithMockk
internal class RabbitMqConsumerStepSpecificationConverterTest :
    AbstractStepSpecificationConverterTest<RabbitMqConsumerStepSpecificationConverter>() {

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
        Assertions.assertTrue(converter.support(relaxedMockk<RabbitMqConsumerStepSpecificationImpl<*>>()))
    }

    @Test
    internal fun `should convert spec with name and queue`() = testDispatcherProvider.runTest {
        // given
        val deserializer = MessageStringDeserializer()
        val spec = RabbitMqConsumerStepSpecificationImpl(deserializer)
        spec.apply {
            name = "my-step"
            connection {
                host = "localhost"
            }
            concurrency(2)
            queue("name1")
        }
        val creationContext = StepCreationContextImpl(scenarioSpecification, directedAcyclicGraph, spec)

        val spiedConverter = spyk(converter)
        val recordsConverter: DatasourceObjectConverter<Delivery, out Any?> = relaxedMockk()

        every {
            spiedConverter.buildConverter(
                eq("my-step"),
                refEq(spec.valueDeserializer)
            )
        } returns recordsConverter

        every { spiedConverter.buildConnectionFactory(refEq(spec.connectionConfiguration)) } returns connectionFactory

        // when
        spiedConverter.convert<Unit, Map<String, *>>(
            creationContext as StepCreationContext<RabbitMqConsumerStepSpecificationImpl<*>>
        )

        // then
        creationContext.createdStep!!.let {
            assertThat(it).isInstanceOf(IterativeDatasourceStep::class).all {
                prop("name").isEqualTo("my-step")
                prop("reader").isNotNull().isInstanceOf(RabbitMqConsumerIterativeReader::class).all {
                    prop("prefetchCount").isEqualTo(10)
                    prop("concurrency").isEqualTo(2)
                    prop("queue").isEqualTo("name1")
                    prop("connectionFactory").isEqualTo(connectionFactory)
                }
                prop("processor").isNotNull().isInstanceOf(NoopDatasourceObjectProcessor::class)
                prop("converter").isNotNull().isSameAs(recordsConverter)
            }
        }
    }

    @Test
    internal fun `should convert spec without name but with queue`() = testDispatcherProvider.runTest {
        // given
        val deserializer = MessageStringDeserializer()
        val spec = RabbitMqConsumerStepSpecificationImpl(deserializer)
        spec.apply {
            connection {
                host = "localhost"
            }
            concurrency(2)
            queue("name2")
        }
        val creationContext = StepCreationContextImpl(scenarioSpecification, directedAcyclicGraph, spec)

        val spiedConverter = spyk(converter)
        val recordsConverter: DatasourceObjectConverter<Delivery, out Any?> = relaxedMockk()
        val stepIdSlot = slot<String>()

        every {
            spiedConverter.buildConverter(
                capture(stepIdSlot),
                refEq(spec.valueDeserializer)
            )
        } returns recordsConverter

        every { spiedConverter.buildConnectionFactory(refEq(spec.connectionConfiguration)) } returns connectionFactory

        // when
        spiedConverter.convert<Unit, Map<String, *>>(
            creationContext as StepCreationContext<RabbitMqConsumerStepSpecificationImpl<*>>
        )

        // then
        creationContext.createdStep!!.let {
            assertThat(it).isInstanceOf(IterativeDatasourceStep::class).all {
                prop("name").isNotNull().isEqualTo(stepIdSlot.captured)
                prop("reader").isNotNull().isInstanceOf(RabbitMqConsumerIterativeReader::class).all {
                    prop("prefetchCount").isEqualTo(10)
                    prop("concurrency").isEqualTo(2)
                    prop("queue").isEqualTo("name2")
                    prop("connectionFactory").isEqualTo(connectionFactory)
                }
                prop("processor").isNotNull().isInstanceOf(NoopDatasourceObjectProcessor::class)
                prop("converter").isNotNull().isSameAs(recordsConverter)
            }
        }
    }

    @Test
    internal fun `should log meters`() = testDispatcherProvider.runTest {
        // given
        val deserializer = MessageStringDeserializer()
        val spec = RabbitMqConsumerStepSpecificationImpl(deserializer)
        spec.monitoringConfig.meters = true
        spec.apply {
            connection {
                host = "localhost"
            }
            concurrency(2)
            queue("name2")
        }
        val creationContext = StepCreationContextImpl(scenarioSpecification, directedAcyclicGraph, spec)

        val spiedConverter = spyk(converter)
        val recordsConverter: DatasourceObjectConverter<Delivery, out Any?> = relaxedMockk()
        val stepIdSlot = slot<String>()

        every {
            spiedConverter.buildConverter(
                capture(stepIdSlot),
                refEq(spec.valueDeserializer)
            )
        } returns recordsConverter

        every { spiedConverter.buildConnectionFactory(refEq(spec.connectionConfiguration)) } returns connectionFactory

        // when
        spiedConverter.convert<Unit, Map<String, *>>(
            creationContext as StepCreationContext<RabbitMqConsumerStepSpecificationImpl<*>>
        )

        // then
        creationContext.createdStep!!.let {
            assertThat(it).isInstanceOf(IterativeDatasourceStep::class).all {
                prop("name").isNotNull().isEqualTo(stepIdSlot.captured)
                prop("reader").isNotNull().isInstanceOf(RabbitMqConsumerIterativeReader::class).all {
                    prop("meterRegistry").isSameAs(meterRegistry)
                    prop("eventsLogger").isNull()
                    prop("prefetchCount").isEqualTo(10)
                    prop("concurrency").isEqualTo(2)
                    prop("queue").isEqualTo("name2")
                    prop("connectionFactory").isEqualTo(connectionFactory)
                }
                prop("processor").isNotNull().isInstanceOf(NoopDatasourceObjectProcessor::class)
                prop("converter").isNotNull().isSameAs(recordsConverter)
            }
        }
    }

    @Test
    internal fun `should log events`() = testDispatcherProvider.runTest {
        // given
        val deserializer = MessageStringDeserializer()
        val spec = RabbitMqConsumerStepSpecificationImpl(deserializer)
        spec.monitoringConfig.events = true
        spec.apply {
            connection {
                host = "localhost"
            }
            concurrency(2)
            queue("name2")
        }
        val creationContext = StepCreationContextImpl(scenarioSpecification, directedAcyclicGraph, spec)

        val spiedConverter = spyk(converter)
        val recordsConverter: DatasourceObjectConverter<Delivery, out Any?> = relaxedMockk()
        val stepIdSlot = slot<String>()

        every {
            spiedConverter.buildConverter(
                capture(stepIdSlot),
                refEq(spec.valueDeserializer)
            )
        } returns recordsConverter

        every { spiedConverter.buildConnectionFactory(refEq(spec.connectionConfiguration)) } returns connectionFactory

        // when
        spiedConverter.convert<Unit, Map<String, *>>(
            creationContext as StepCreationContext<RabbitMqConsumerStepSpecificationImpl<*>>
        )

        // then
        creationContext.createdStep!!.let {
            assertThat(it).isInstanceOf(IterativeDatasourceStep::class).all {
                prop("name").isNotNull().isEqualTo(stepIdSlot.captured)
                prop("reader").isNotNull().isInstanceOf(RabbitMqConsumerIterativeReader::class).all {
                    prop("meterRegistry").isNull()
                    prop("eventsLogger").isSameAs(eventsLogger)
                    prop("prefetchCount").isEqualTo(10)
                    prop("concurrency").isEqualTo(2)
                    prop("queue").isEqualTo("name2")
                    prop("connectionFactory").isEqualTo(connectionFactory)
                }
                prop("processor").isNotNull().isInstanceOf(NoopDatasourceObjectProcessor::class)
                prop("converter").isNotNull().isSameAs(recordsConverter)
            }
        }
    }
}