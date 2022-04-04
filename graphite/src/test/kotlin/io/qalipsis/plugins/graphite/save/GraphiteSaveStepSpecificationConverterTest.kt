package io.qalipsis.plugins.graphite.save

import assertk.all
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isSameAs
import assertk.assertions.isTrue
import io.mockk.spyk
import io.netty.channel.nio.NioEventLoopGroup
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.steps.StepCreationContext
import io.qalipsis.api.steps.StepCreationContextImpl
import io.qalipsis.test.assertk.prop
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.relaxedMockk
import io.qalipsis.test.steps.AbstractStepSpecificationConverterTest
import kotlinx.coroutines.test.runBlockingTest
import org.junit.jupiter.api.Test

/**
 *
 * @author Palina Bril
 */
@WithMockk
internal class GraphiteSaveStepSpecificationConverterTest :
    AbstractStepSpecificationConverterTest<GraphiteSaveStepSpecificationConverter>() {

    private val recordSupplier: (suspend (ctx: StepContext<*, *>, input: Any?) -> List<String>) = { _, _ ->
        listOf(
            "foo 1.1\n", "foo 1.2\n", "foo 1.3\n"
        )
    }

    @Test
    override fun `should not support unexpected spec`() {
        assertThat(converter.support(relaxedMockk()))
            .isFalse()
    }

    @Test
    override fun `should support expected spec`() {
        assertThat(converter.support(relaxedMockk<GraphiteSaveStepSpecificationImpl<*>>()))
            .isTrue()

    }

    @Test
    fun `should convert with name, retry policy and meters`() = runBlockingTest {
        // given
        val spec = GraphiteSaveStepSpecificationImpl<Any>()
        spec.also {
            it.name = "graphite-save-step"
            it.records = recordSupplier
            it.connect {
                server("localhost", 8080)
                workerGroup { NioEventLoopGroup() }
            }
            it.monitoring {
                meters = true
                events = false
            }
        }
        val creationContext = StepCreationContextImpl(scenarioSpecification, directedAcyclicGraph, spec)
        val spiedConverter = spyk(converter, recordPrivateCalls = true)
        // when
        spiedConverter.convert<Unit, Map<String, *>>(
            creationContext as StepCreationContext<GraphiteSaveStepSpecificationImpl<*>>
        )

        // then
        assertThat(creationContext.createdStep!!).all {
            prop("id").isEqualTo("graphite-save-step")
            prop("graphiteSaveMessageClient").all {
                prop("clientBuilder").isNotNull()
                prop("meterRegistry").isSameAs(meterRegistry)
                prop("eventsLogger").isNull()
            }
            prop("messageFactory").isSameAs(recordSupplier)
        }
    }

    @Test
    fun `should convert without name and retry policy but with events`() = runBlockingTest {
        // given
        val spec = GraphiteSaveStepSpecificationImpl<Any>()
        spec.also {
            it.records = recordSupplier
            it.connect {
                server("localhost", 8080)
                workerGroup { NioEventLoopGroup() }
            }
            it.monitoring {
                events = true
            }
        }
        val creationContext = StepCreationContextImpl(scenarioSpecification, directedAcyclicGraph, spec)
        val spiedConverter = spyk(converter, recordPrivateCalls = true)


        // when
        spiedConverter.convert<Unit, Map<String, *>>(
            creationContext as StepCreationContext<GraphiteSaveStepSpecificationImpl<*>>
        )

        // then
        assertThat(creationContext.createdStep!!).all {
            prop("retryPolicy").isNull()
            prop("messageFactory").isSameAs(recordSupplier)
            prop("graphiteSaveMessageClient").all {
                prop("clientBuilder").isNotNull()
                prop("meterRegistry").isNull()
                prop("eventsLogger").isSameAs(eventsLogger)
            }
        }
    }
}
