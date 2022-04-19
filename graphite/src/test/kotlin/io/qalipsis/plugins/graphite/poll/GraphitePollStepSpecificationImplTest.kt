package io.qalipsis.plugins.graphite.poll

import assertk.all
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isTrue
import assertk.assertions.prop
import io.qalipsis.api.scenario.StepSpecificationRegistry
import io.qalipsis.api.scenario.TestScenarioFactory
import io.qalipsis.api.steps.SingletonConfiguration
import io.qalipsis.api.steps.SingletonType
import io.qalipsis.api.steps.StepMonitoringConfiguration
import io.qalipsis.plugins.graphite.graphite
import io.qalipsis.plugins.graphite.poll.model.GraphiteQuery
import org.junit.jupiter.api.Test
import java.time.Duration

internal class GraphitePollStepSpecificationImplTest {

    @Test
    fun `should add minimal specification to the scenario with default values`() {
        val scenario = TestScenarioFactory.scenario("my-scenario") as StepSpecificationRegistry
        scenario.graphite().poll {
            name = "my-step"
        }
        assertThat(scenario.rootSteps.first()).isInstanceOf(GraphitePollStepSpecificationImpl::class).all {
            prop(GraphitePollStepSpecificationImpl::name).isEqualTo("my-step")
            prop(GraphitePollStepSpecificationImpl::pollPeriod).isEqualTo(
                Duration.ofSeconds(10L)
            )
            prop(GraphitePollStepSpecificationImpl::monitoringConfiguration).all {
                prop(StepMonitoringConfiguration::events).isFalse()
                prop(StepMonitoringConfiguration::meters).isFalse()
            }
            prop(GraphitePollStepSpecificationImpl::singletonConfiguration).all {
                prop(SingletonConfiguration::type).isEqualTo(SingletonType.UNICAST)
                prop(SingletonConfiguration::bufferSize).isEqualTo(-1)
                prop(SingletonConfiguration::idleTimeout).isEqualTo(Duration.ZERO)
            }
        }
    }

    @Test
    fun `should add a complete specification to the scenario as broadcast whit monitoring`() {
        val scenario = TestScenarioFactory.scenario("my-scenario") as StepSpecificationRegistry
        scenario.graphite().poll {
            name = "my-step"
            connect {
                server("http://localhost:8086")
                basicAuthentication("myUser", "password")
            }
            pollDelay(Duration.ofSeconds(1L))
            monitoring {
                events = false
                meters = true
            }
            query(GraphiteQuery("target.key"))
            broadcast(123, Duration.ofSeconds(20))
        }
        assertThat(scenario.rootSteps.first()).isInstanceOf(GraphitePollStepSpecificationImpl::class).all {
            prop(GraphitePollStepSpecificationImpl::name).isEqualTo("my-step")

            prop(GraphitePollStepSpecificationImpl::pollPeriod).isEqualTo(Duration.ofSeconds(1L))
            prop(GraphitePollStepSpecificationImpl::monitoringConfiguration).all {
                prop(StepMonitoringConfiguration::events).isFalse()
                prop(StepMonitoringConfiguration::meters).isTrue()
            }
            prop(GraphitePollStepSpecificationImpl::connectionConfiguration).isInstanceOf(
                GraphiteSearchConnectionSpecificationImpl::class
            )
                .all {
                    prop(GraphiteSearchConnectionSpecificationImpl::url).isEqualTo("http://localhost:8086")
                    prop(GraphiteSearchConnectionSpecificationImpl::username).isEqualTo("myUser")
                    prop(GraphiteSearchConnectionSpecificationImpl::password).isEqualTo("password")
                }
            prop(GraphitePollStepSpecificationImpl::singletonConfiguration).all {
                prop(SingletonConfiguration::type).isEqualTo(SingletonType.BROADCAST)
                prop(SingletonConfiguration::bufferSize).isEqualTo(123)
                prop(SingletonConfiguration::idleTimeout).isEqualTo(Duration.ofSeconds(20))
            }
            prop(GraphitePollStepSpecificationImpl::queryBuilder).all {
                prop(GraphiteQuery::target).isEqualTo("target.key")
            }
        }
    }
}
