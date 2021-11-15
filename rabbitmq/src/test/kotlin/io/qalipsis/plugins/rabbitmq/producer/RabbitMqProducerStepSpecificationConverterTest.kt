package io.qalipsis.plugins.rabbitmq.producer

import assertk.all
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import com.rabbitmq.client.ConnectionFactory
import io.micrometer.core.instrument.Counter
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.spyk
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.steps.StepCreationContext
import io.qalipsis.api.steps.StepCreationContextImpl
import io.qalipsis.plugins.rabbitmq.producer.catadioptre.buildMetrics
import io.qalipsis.test.assertk.prop
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.relaxedMockk
import io.qalipsis.test.mockk.verifyOnce
import io.qalipsis.test.steps.AbstractStepSpecificationConverterTest
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runBlockingTest
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

/**
 *
 * @author Alexander Sosnovsky
 */
@Suppress("UNCHECKED_CAST")
@WithMockk
internal class RabbitMqProducerStepSpecificationConverterTest :
    AbstractStepSpecificationConverterTest<RabbitMqProducerStepSpecificationConverter>() {

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
    internal fun `should convert spec with name`() = runBlockingTest {
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

        val recordSupplier: (suspend (ctx: StepContext<*, *>, input: Any?) -> List<RabbitMqProducerRecord>) = { _, _ -> listOf(rec1, rec2) }

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
        val spiedConverter = spyk(converter, recordPrivateCalls = true)
        val producerMetrics: RabbitMqProducerMetrics = relaxedMockk()
        every {
            spiedConverter["buildMetrics"](
                refEq(spec.metrics),
                eq("my-step")
            )
        } returns producerMetrics

        every { spiedConverter["buildConnectionFactory"](refEq(spec.connectionConfiguration)) } returns connectionFactory

        // when
        runBlocking {
            spiedConverter.convert<Unit, Map<String, *>>(
                creationContext as StepCreationContext<RabbitMqProducerStepSpecificationImpl<*>>
            )
        }

        // then
        creationContext.createdStep!!.let {
            assertThat(it).isInstanceOf(RabbitMqProducerStep::class).all {
                prop("id").isEqualTo("my-step")
                prop("recordFactory").isEqualTo(recordSupplier)
                prop("rabbitMqProducer").isNotNull().all {
                    prop("connectionFactory").isEqualTo(connectionFactory)
                }
                prop("retryPolicy").isNull()
            }
        }
    }


    @Test
    internal fun `should build converter`() {
        // given
        val monitoringConfiguration = RabbitMqProducerMetricsConfiguration()

        // when
        val producerMetrics = converter.buildMetrics(monitoringConfiguration, "my-step")

        // then
        assertThat(producerMetrics).isNotNull().isInstanceOf(RabbitMqProducerMetrics::class).all {
            prop("producedBytesCounter").isNull()
            prop("producedRecordsCounter").isNull()
        }
    }

    @Test
    internal fun `should build converter with key bytes counter`() {
        // given
        val monitoringConfiguration = RabbitMqProducerMetricsConfiguration(bytesCount = true)

        // when
        val producerMetrics = converter.buildMetrics(monitoringConfiguration, "my-step")

        // then
        assertThat(producerMetrics).isNotNull().isInstanceOf(RabbitMqProducerMetrics::class).all {
            prop("producedBytesCounter").isNotNull().isInstanceOf(Counter::class)
            prop("producedRecordsCounter").isNull()
        }
        verifyOnce {
            meterRegistry.counter("rabbitmq-producer-sent-bytes", "step", "my-step")
        }
        confirmVerified(meterRegistry)
    }

    @Test
    internal fun `should build converter with records counter`() {
        // given
        val monitoringConfiguration = RabbitMqProducerMetricsConfiguration(recordsCount = true)

        // when
        val producerMetrics = converter.buildMetrics(monitoringConfiguration, "my-step")

        // then
        assertThat(producerMetrics).isNotNull().isInstanceOf(RabbitMqProducerMetrics::class).all {
            prop("producedBytesCounter").isNull()
            prop("producedRecordsCounter").isNotNull().isInstanceOf(Counter::class)
        }
        verifyOnce {
            meterRegistry.counter("rabbitmq-producer-records", "step", "my-step")
        }
        confirmVerified(meterRegistry)
    }
}
