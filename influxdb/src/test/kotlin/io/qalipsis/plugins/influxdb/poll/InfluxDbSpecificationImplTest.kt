package io.qalipsis.plugins.influxdb.poll

import assertk.all
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isTrue
import assertk.assertions.prop
import io.qalipsis.api.scenario.StepSpecificationRegistry
import io.qalipsis.api.scenario.scenario
import io.qalipsis.api.steps.SingletonConfiguration
import io.qalipsis.api.steps.SingletonType
import io.qalipsis.api.steps.StepMonitoringConfiguration
import io.qalipsis.plugins.influxdb.InfluxDbStepConnectionImpl
import io.qalipsis.plugins.influxdb.influxdb
import org.junit.jupiter.api.Test
import java.time.Duration

internal class InfluxDbSpecificationImplTest {

    @Test
    internal fun `should add minimal specification to the scenario with default values`() {
        val scenario = scenario("my-scenario") as StepSpecificationRegistry
        scenario.influxdb().poll {
            name = "my-step"
        }
        assertThat(scenario.rootSteps.first()).isInstanceOf(InfluxDbPollStepSpecificationImpl::class).all {
            prop(InfluxDbPollStepSpecificationImpl::name).isEqualTo("my-step")
            prop(InfluxDbPollStepSpecificationImpl::pollPeriod).isEqualTo(
                Duration.ofSeconds(10L)
            )
            prop(InfluxDbPollStepSpecificationImpl::monitoringConfiguration).all {
                prop(StepMonitoringConfiguration::events).isFalse()
                prop(StepMonitoringConfiguration::meters).isFalse()
            }
            prop(InfluxDbPollStepSpecificationImpl::singletonConfiguration).all {
                prop(SingletonConfiguration::type).isEqualTo(SingletonType.UNICAST)
                prop(SingletonConfiguration::bufferSize).isEqualTo(-1)
                prop(SingletonConfiguration::idleTimeout).isEqualTo(Duration.ZERO)
            }
        }
    }

    @Test
    internal fun `should add a complete specification to the scenario as broadcast whit monitoring`() {
        val scenario = scenario("my-scenario") as StepSpecificationRegistry
        scenario.influxdb().poll {
            name = "my-step"
            connect {
                server("http://127.0.0.1:8086", "DB", "org")
                basic("user", "pass")
            }
            pollDelay(Duration.ofSeconds(1L))
            monitoring {
                events = false
                meters = true
            }
            broadcast(123, Duration.ofSeconds(20))
        }
        assertThat(scenario.rootSteps.first()).isInstanceOf(InfluxDbPollStepSpecificationImpl::class).all {
            prop(InfluxDbPollStepSpecificationImpl::name).isEqualTo("my-step")

            prop(InfluxDbPollStepSpecificationImpl::pollPeriod).isEqualTo(Duration.ofSeconds(1L))
            prop(InfluxDbPollStepSpecificationImpl::monitoringConfiguration).all {
                prop(StepMonitoringConfiguration::events).isFalse()
                prop(StepMonitoringConfiguration::meters).isTrue()
            }
            prop(InfluxDbPollStepSpecificationImpl::connectionConfiguration).isInstanceOf(InfluxDbStepConnectionImpl::class)
                .all {
                    prop(InfluxDbStepConnectionImpl::bucket).isEqualTo("DB")
                    prop(InfluxDbStepConnectionImpl::user).isEqualTo("user")
                    prop(InfluxDbStepConnectionImpl::url).isEqualTo("http://127.0.0.1:8086")
                    prop(InfluxDbStepConnectionImpl::password).isEqualTo("pass")
                }
            prop(InfluxDbPollStepSpecificationImpl::singletonConfiguration).all {
                prop(SingletonConfiguration::type).isEqualTo(SingletonType.BROADCAST)
                prop(SingletonConfiguration::bufferSize).isEqualTo(123)
                prop(SingletonConfiguration::idleTimeout).isEqualTo(Duration.ofSeconds(20))
            }
        }
    }
}
