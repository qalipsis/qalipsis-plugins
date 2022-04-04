package io.qalipsis.plugins.graphite.save

import assertk.all
import assertk.assertThat
import assertk.assertions.*
import io.aerisconsulting.catadioptre.getProperty
import io.netty.channel.nio.NioEventLoopGroup
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.steps.DummyStepSpecification
import io.qalipsis.api.steps.StepMonitoringConfiguration
import io.qalipsis.plugins.graphite.graphite
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.relaxedMockk
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension


/**
 *
 * @author Palina Bril
 */
internal class GraphiteSaveStepSpecificationImplTest {

    private val recordSupplier: (suspend (ctx: StepContext<*, *>, input: Any?) -> List<String>) = { _, _ ->
        listOf(
            "foo 1.1\n", "foo 1.2\n", "foo 1.3\n"
        )
    }

    @JvmField
    @RegisterExtension
    val testDispatcherProvider = TestDispatcherProvider()

    @Test
    fun `should add minimal configuration for the step`() = testDispatcherProvider.runTest {
        val previousStep = DummyStepSpecification()
        previousStep.graphite().save {
            name = "my-save-step"
            connect {
                server("localhost", 8080)
                workerGroup { NioEventLoopGroup() }
            }
            records(recordSupplier)
        }

        assertThat(previousStep.nextSteps[0]).isInstanceOf(GraphiteSaveStepSpecificationImpl::class).all {
            prop("name") { GraphiteSaveStepSpecificationImpl<*>::name.call(it) }.isEqualTo("my-save-step")
            prop(GraphiteSaveStepSpecificationImpl<*>::connectionConfig).isNotNull()
            prop(GraphiteSaveStepSpecificationImpl<*>::records).isEqualTo(recordSupplier)
            prop(GraphiteSaveStepSpecificationImpl<*>::monitoringConfig).isNotNull().all {
                prop(StepMonitoringConfiguration::events).isFalse()
                prop(StepMonitoringConfiguration::meters).isFalse()
            }
        }

        val step: GraphiteSaveStepSpecificationImpl<*> =
            previousStep.nextSteps[0] as GraphiteSaveStepSpecificationImpl<*>

        val messages = step.getProperty<suspend (ctx: StepContext<*, *>, input: Int) -> String>("records")
        assertThat(messages(relaxedMockk(), relaxedMockk())).isEqualTo(listOf("foo 1.1\n", "foo 1.2\n", "foo 1.3\n"))
    }


    @Test
    fun `should add a complete configuration for the step`() = testDispatcherProvider.runTest {
        val previousStep = DummyStepSpecification()
        previousStep.graphite().save {
            name = "my-save-step"
            connect {
                server("localhost", 8080)
                workerGroup { NioEventLoopGroup() }
            }
            records(recordSupplier)
            monitoring {
                events = true
                meters = true
            }
        }

        assertThat(previousStep.nextSteps[0]).isInstanceOf(GraphiteSaveStepSpecificationImpl::class).all {
            prop("name") { GraphiteSaveStepSpecificationImpl<*>::name.call(it) }.isEqualTo("my-save-step")
            prop(GraphiteSaveStepSpecificationImpl<*>::connectionConfig).isNotNull()
            prop(GraphiteSaveStepSpecificationImpl<*>::records).isEqualTo(recordSupplier)
            prop(GraphiteSaveStepSpecificationImpl<*>::monitoringConfig).all {
                prop(StepMonitoringConfiguration::events).isTrue()
                prop(StepMonitoringConfiguration::meters).isTrue()
            }
        }

        val step: GraphiteSaveStepSpecificationImpl<*> =
            previousStep.nextSteps[0] as GraphiteSaveStepSpecificationImpl<*>

        val messages = step.getProperty<suspend (ctx: StepContext<*, *>, input: Int) -> String>("records")
        assertThat(messages(relaxedMockk(), relaxedMockk())).isEqualTo(listOf("foo 1.1\n", "foo 1.2\n", "foo 1.3\n"))
    }
}
