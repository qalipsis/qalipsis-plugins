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
import io.aerisconsulting.catadioptre.invokeInvisible
import io.micrometer.core.instrument.Counter
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.slot
import io.mockk.spyk
import io.qalipsis.api.messaging.deserializer.MessageJsonDeserializer
import io.qalipsis.api.messaging.deserializer.MessageStringDeserializer
import io.qalipsis.api.steps.StepCreationContext
import io.qalipsis.api.steps.StepCreationContextImpl
import io.qalipsis.api.steps.datasource.DatasourceObjectConverter
import io.qalipsis.api.steps.datasource.IterativeDatasourceStep
import io.qalipsis.api.steps.datasource.processors.NoopDatasourceObjectProcessor
import io.qalipsis.plugins.rabbitmq.consumer.converter.RabbitMqConsumerConverter
import io.qalipsis.test.assertk.prop
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.relaxedMockk
import io.qalipsis.test.mockk.verifyOnce
import io.qalipsis.test.steps.AbstractStepSpecificationConverterTest
import kotlinx.coroutines.test.runBlockingTest
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

/**
 * @author Gabriel Moraes
 */
@Suppress("UNCHECKED_CAST")
@WithMockk
internal class RabbitMqConsumerStepSpecificationConverterTest :
    AbstractStepSpecificationConverterTest<RabbitMqConsumerStepSpecificationConverter>() {

    @RelaxedMockK
    private lateinit var mockkedConnectionFactory: ConnectionFactory

    @Test
    override fun `should not support unexpected spec`() {
        Assertions.assertFalse(converter.support(relaxedMockk()))

    }

    @Test
    override fun `should support expected spec`() {
        Assertions.assertTrue(converter.support(relaxedMockk<RabbitMqConsumerStepSpecificationImpl<*>>()))
    }

    @Test
    internal fun `should convert spec with name and queue`() = runBlockingTest {
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

        val spiedConverter = spyk(converter, recordPrivateCalls = true)
        val recordsConverter: DatasourceObjectConverter<Delivery, out Any?> = relaxedMockk()

        every {
            spiedConverter["buildConverter"](
                eq("my-step"),
                refEq(spec.valueDeserializer),
                refEq(spec.metrics)
            )
        } returns recordsConverter

        every { spiedConverter["buildConnectionFactory"](refEq(spec.connectionConfiguration)) } returns mockkedConnectionFactory

        // when
        spiedConverter.convert<Unit, Map<String, *>>(
            creationContext as StepCreationContext<RabbitMqConsumerStepSpecificationImpl<*>>
        )

        // then
        creationContext.createdStep!!.let {
            assertThat(it).isInstanceOf(IterativeDatasourceStep::class).all {
                prop("id").isEqualTo("my-step")
                prop("reader").isNotNull().isInstanceOf(RabbitMqConsumerIterativeReader::class).all {
                    prop("prefetchCount").isEqualTo(10)
                    prop("concurrency").isEqualTo(2)
                    prop("queue").isEqualTo("name1")
                    prop("connectionFactory").isEqualTo(mockkedConnectionFactory)
                }
                prop("processor").isNotNull().isInstanceOf(NoopDatasourceObjectProcessor::class)
                prop("converter").isNotNull().isSameAs(recordsConverter)
            }
        }
    }

    @Test
    internal fun `should convert spec without name but with queue`() = runBlockingTest {
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

        val spiedConverter = spyk(converter, recordPrivateCalls = true)
        val recordsConverter: DatasourceObjectConverter<Delivery, out Any?> = relaxedMockk()
        val stepIdSlot = slot<String>()

        every {
            spiedConverter["buildConverter"](
                capture(stepIdSlot),
                refEq(spec.valueDeserializer),
                refEq(spec.metrics)
            )
        } returns recordsConverter

        every { spiedConverter["buildConnectionFactory"](refEq(spec.connectionConfiguration)) } returns mockkedConnectionFactory

        // when
        spiedConverter.convert<Unit, Map<String, *>>(
            creationContext as StepCreationContext<RabbitMqConsumerStepSpecificationImpl<*>>
        )

        // then
        creationContext.createdStep!!.let {
            assertThat(it).isInstanceOf(IterativeDatasourceStep::class).all {
                prop("id").isNotNull().isEqualTo(stepIdSlot.captured)
                prop("reader").isNotNull().isInstanceOf(RabbitMqConsumerIterativeReader::class).all {
                    prop("prefetchCount").isEqualTo(10)
                    prop("concurrency").isEqualTo(2)
                    prop("queue").isEqualTo("name2")
                    prop("connectionFactory").isEqualTo(mockkedConnectionFactory)
                }
                prop("processor").isNotNull().isInstanceOf(NoopDatasourceObjectProcessor::class)
                prop("converter").isNotNull().isSameAs(recordsConverter)
            }
        }
    }

    @Test
    internal fun `should build single converter`() {

        val monitoringConfiguration = RabbitMqConsumerMetricsConfiguration()
        val valueDeserializer = MessageStringDeserializer()

        // when
        val recordsConverter = converter.invokeInvisible<DatasourceObjectConverter<Delivery, out Any?>>("buildConverter","my-step", valueDeserializer, monitoringConfiguration)

        // then
        assertThat(recordsConverter).isNotNull().isInstanceOf(RabbitMqConsumerConverter::class).all {
            prop("valueDeserializer").isSameAs(valueDeserializer)
            prop("consumedValueBytesCounter").isNull()
            prop("consumedRecordsCounter").isNull()
        }
    }

    @Test
    internal fun `should build single converter with json deserializer`() {

        val monitoringConfiguration = RabbitMqConsumerMetricsConfiguration()
        val jsonValueDeserializer = MessageJsonDeserializer(String::class)

        // when
        val recordsConverter = converter.invokeInvisible<DatasourceObjectConverter<Delivery, out Any?>>("buildConverter","my-step", jsonValueDeserializer, monitoringConfiguration)

        // then
        assertThat(recordsConverter).isNotNull().isInstanceOf(RabbitMqConsumerConverter::class).all {
            prop("valueDeserializer").isSameAs(jsonValueDeserializer)
            prop("consumedValueBytesCounter").isNull()
            prop("consumedRecordsCounter").isNull()
        }
    }

    @Test
    internal fun `should build converter with value bytes counter`() {
        val monitoringConfiguration = RabbitMqConsumerMetricsConfiguration(valuesBytesCount = true)
        val valueDeserializer = MessageStringDeserializer()
        // when
        val recordsConverter = converter.invokeInvisible<DatasourceObjectConverter<Delivery, out Any?>>("buildConverter","my-step", valueDeserializer, monitoringConfiguration)

        // then
        assertThat(recordsConverter).isNotNull().isInstanceOf(RabbitMqConsumerConverter::class).all {
            prop("valueDeserializer").isSameAs(valueDeserializer)
            prop("consumedValueBytesCounter").isNotNull().isInstanceOf(Counter::class)
            prop("consumedRecordsCounter").isNull()
        }
        verifyOnce {
            meterRegistry.counter("rabbitmq-consumer-value-bytes", "step", "my-step")
        }
        confirmVerified(meterRegistry)
    }

    @Test
    internal fun `should build converter with records counter`() {
        val monitoringConfiguration = RabbitMqConsumerMetricsConfiguration(recordsCount = true)
        val valueDeserializer = MessageStringDeserializer()
        // when
        val recordsConverter = converter.invokeInvisible<DatasourceObjectConverter<Delivery, out Any?>>("buildConverter","my-step", valueDeserializer, monitoringConfiguration)

        // then
        assertThat(recordsConverter).isNotNull().isInstanceOf(RabbitMqConsumerConverter::class).all {
            prop("valueDeserializer").isSameAs(valueDeserializer)
            prop("consumedValueBytesCounter").isNull()
            prop("consumedRecordsCounter").isNotNull().isInstanceOf(Counter::class)
        }
        verifyOnce {
            meterRegistry.counter("rabbitmq-consumer-records", "step", "my-step")
        }
        confirmVerified(meterRegistry)
    }

}
