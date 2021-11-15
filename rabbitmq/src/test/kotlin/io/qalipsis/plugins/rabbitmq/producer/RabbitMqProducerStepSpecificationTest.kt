package io.qalipsis.plugins.rabbitmq.producer

import assertk.all
import assertk.assertThat
import assertk.assertions.*
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.steps.DummyStepSpecification
import io.qalipsis.plugins.rabbitmq.configuration.RabbitMqConnectionConfiguration
import io.qalipsis.plugins.rabbitmq.rabbitmq
import org.junit.jupiter.api.Test

/**
 *
 * @author Alexander Sosnovsky
 * */
internal class RabbitMqProducerStepSpecificationTest {

    @Test
    internal fun `should add minimal specification to the scenario with default values`() {

        val previousStep = DummyStepSpecification()
        previousStep.rabbitmq().produce {
            name = "my-producer-step"
        }

        assertThat(previousStep.nextSteps[0]).isInstanceOf(RabbitMqProducerStepSpecificationImpl::class).all {
            prop("name") { RabbitMqProducerStepSpecificationImpl<*>::name.call(it) }.isEqualTo("my-producer-step")

            prop(RabbitMqProducerStepSpecificationImpl<*>::connectionConfiguration).all {
                prop(RabbitMqConnectionConfiguration::host).isEqualTo("localhost")
                prop(RabbitMqConnectionConfiguration::port).isEqualTo(5672)
                prop(RabbitMqConnectionConfiguration::password).isEqualTo("guest")
                prop(RabbitMqConnectionConfiguration::username).isEqualTo("guest")
                prop(RabbitMqConnectionConfiguration::virtualHost).isEqualTo("/")
                prop(RabbitMqConnectionConfiguration::clientProperties).isEqualTo(emptyMap())
            }

            prop(RabbitMqProducerStepSpecificationImpl<*>::concurrency).isEqualTo(1)

            prop(RabbitMqProducerStepSpecificationImpl<*>::metrics).isNotNull().all {
                prop(RabbitMqProducerMetricsConfiguration::bytesCount).isFalse()
                prop(RabbitMqProducerMetricsConfiguration::recordsCount).isFalse()
            }
        }
    }

    @Test
    internal fun `should add a complete specification to the scenario`() {
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

        val previousStep = DummyStepSpecification()
        previousStep.rabbitmq().produce {
            name = "my-producer-step"
            concurrency(10)
            connection {
                host = "anotherhost"
                port = 5673
                password = "pass"
                username = "test"
                virtualHost = "/guest"
                clientProperties = mapOf("t" to "test")
            }
            records(recordSupplier)
        }

        assertThat(previousStep.nextSteps[0]).isInstanceOf(RabbitMqProducerStepSpecificationImpl::class).all {
            prop("name") { RabbitMqProducerStepSpecificationImpl<*>::name.call(it) }.isEqualTo("my-producer-step")

            prop(RabbitMqProducerStepSpecificationImpl<*>::connectionConfiguration).all {
                prop(RabbitMqConnectionConfiguration::host).isEqualTo("anotherhost")
                prop(RabbitMqConnectionConfiguration::port).isEqualTo(5673)
                prop(RabbitMqConnectionConfiguration::password).isEqualTo("pass")
                prop(RabbitMqConnectionConfiguration::username).isEqualTo("test")
                prop(RabbitMqConnectionConfiguration::virtualHost).isEqualTo("/guest")
                prop(RabbitMqConnectionConfiguration::clientProperties).isEqualTo(mapOf("t" to "test"))
            }

            prop(RabbitMqProducerStepSpecificationImpl<*>::concurrency).isEqualTo(10)

            prop(RabbitMqProducerStepSpecificationImpl<*>::recordsFactory).isEqualTo(recordSupplier)

            prop(RabbitMqProducerStepSpecificationImpl<*>::metrics).isNotNull().all {
                prop(RabbitMqProducerMetricsConfiguration::bytesCount).isFalse()
                prop(RabbitMqProducerMetricsConfiguration::recordsCount).isFalse()
            }
        }
    }


    @Test
    internal fun `should apply bytes count`() {
        val scenario = DummyStepSpecification()
        
        scenario.rabbitmq().produce {
            metrics {
                bytesCount = true
            }
        }

        assertThat(scenario.nextSteps[0]).isInstanceOf(RabbitMqProducerStepSpecificationImpl::class).all {
            prop(RabbitMqProducerStepSpecificationImpl<*>::metrics).isNotNull().all {
                prop(RabbitMqProducerMetricsConfiguration::bytesCount).isTrue()
                prop(RabbitMqProducerMetricsConfiguration::recordsCount).isFalse()
            }
        }
    }

    @Test
    internal fun `should apply records count`() {
        val scenario = DummyStepSpecification()
        
        scenario.rabbitmq().produce {
            metrics {
                recordsCount = true
            }
        }

        assertThat(scenario.nextSteps[0]).isInstanceOf(RabbitMqProducerStepSpecificationImpl::class).all {
            prop(RabbitMqProducerStepSpecificationImpl<*>::metrics).isNotNull().all {
                prop(RabbitMqProducerMetricsConfiguration::bytesCount).isFalse()
                prop(RabbitMqProducerMetricsConfiguration::recordsCount).isTrue()
            }
        }
    }

}
