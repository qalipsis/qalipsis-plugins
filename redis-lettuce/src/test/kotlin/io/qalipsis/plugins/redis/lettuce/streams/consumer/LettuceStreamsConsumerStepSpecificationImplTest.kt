package io.qalipsis.plugins.redis.lettuce.streams.consumer

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
import io.qalipsis.plugins.redis.lettuce.configuration.RedisConnectionConfiguration
import io.qalipsis.plugins.redis.lettuce.configuration.RedisConnectionType
import io.qalipsis.plugins.redis.lettuce.redisLettuce
import io.qalipsis.test.coroutines.TestDispatcherProvider
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.Duration

internal class LettuceStreamsConsumerStepSpecificationImplTest {

    @JvmField
    @RegisterExtension
    val testDispatcherProvider = TestDispatcherProvider()

    @Test
    internal fun `should add minimal specification to the scenario with default values`() =
        testDispatcherProvider.runTest {
            val scenario = scenario("my-scenario") as StepSpecificationRegistry
            scenario.redisLettuce().streamsConsume {
                name = "my-step"
                streamKey("test")
                group("group")
            }

            assertThat(scenario.rootSteps.first()).isInstanceOf(LettuceStreamsConsumerStepSpecificationImpl::class)
                .all {
                    prop(LettuceStreamsConsumerStepSpecificationImpl::name).isEqualTo("my-step")
                prop(LettuceStreamsConsumerStepSpecificationImpl::connection).all {
                    prop(RedisConnectionConfiguration::nodes).isEqualTo(listOf("localhost:6379"))
                    prop(RedisConnectionConfiguration::database).isEqualTo(0)
                    prop(RedisConnectionConfiguration::authPassword).isEqualTo("")
                    prop(RedisConnectionConfiguration::redisConnectionType).isEqualTo(RedisConnectionType.SINGLE)
                    prop(RedisConnectionConfiguration::authUser).isEqualTo("")
                    prop(RedisConnectionConfiguration::masterId).isEqualTo("")
                }

                prop(LettuceStreamsConsumerStepSpecificationImpl::singletonConfiguration).all {
                    prop(SingletonConfiguration::type).isEqualTo(SingletonType.UNICAST)
                    prop(SingletonConfiguration::bufferSize).isEqualTo(-1)
                    prop(SingletonConfiguration::idleTimeout).isEqualTo(Duration.ZERO)
                }

                prop(LettuceStreamsConsumerStepSpecificationImpl::flattenOutput).isFalse()
                prop(LettuceStreamsConsumerStepSpecificationImpl::concurrency).isEqualTo(1)
                prop(LettuceStreamsConsumerStepSpecificationImpl::groupName).isEqualTo("group")
                prop(LettuceStreamsConsumerStepSpecificationImpl::offset).isEqualTo(LettuceStreamsConsumerOffset.FROM_BEGINNING)
                prop(LettuceStreamsConsumerStepSpecificationImpl::streamKey).isEqualTo("test")

                prop(LettuceStreamsConsumerStepSpecificationImpl::monitoringConfig).all {
                    prop(StepMonitoringConfiguration::events).isFalse()
                    prop(StepMonitoringConfiguration::meters).isFalse()
                }
            }
    }

    @Test
    internal fun `should add a complete specification to the scenario as broadcast`() = testDispatcherProvider.runTest {

        val scenario = scenario("my-scenario") as StepSpecificationRegistry
        scenario.redisLettuce().streamsConsume {
            name = "my-step"
            connection {
                nodes = listOf("localhost:6379")
                database = 1
                redisConnectionType = RedisConnectionType.CLUSTER
                authPassword = "root"
                authUser = "default"
                masterId = "mymaster"
            }
            monitoring {
                events = true
                meters = true
            }
            streamKey("test")
            group("group")
            concurrency(10)
            unicast(6, Duration.ofDays(1))
        }.flatten()


        assertThat(scenario.rootSteps.first()).isInstanceOf(LettuceStreamsConsumerStepSpecificationImpl::class)
            .all {
                prop(LettuceStreamsConsumerStepSpecificationImpl::name).isEqualTo("my-step")
                prop(LettuceStreamsConsumerStepSpecificationImpl::connection).all {
                    prop(RedisConnectionConfiguration::nodes).isEqualTo(listOf("localhost:6379"))
                    prop(RedisConnectionConfiguration::database).isEqualTo(1)
                    prop(RedisConnectionConfiguration::authPassword).isEqualTo("root")
                    prop(RedisConnectionConfiguration::redisConnectionType).isEqualTo(RedisConnectionType.CLUSTER)
                    prop(RedisConnectionConfiguration::authUser).isEqualTo("default")
                    prop(RedisConnectionConfiguration::masterId).isEqualTo("mymaster")
                }


                prop(LettuceStreamsConsumerStepSpecificationImpl::singletonConfiguration).all {
                    prop(SingletonConfiguration::type).isEqualTo(SingletonType.UNICAST)
                    prop(SingletonConfiguration::bufferSize).isEqualTo(6)
                    prop(SingletonConfiguration::idleTimeout).isEqualTo(Duration.ofDays(1))
                }

                prop(LettuceStreamsConsumerStepSpecificationImpl::flattenOutput).isTrue()
                prop(LettuceStreamsConsumerStepSpecificationImpl::concurrency).isEqualTo(10)
                prop(LettuceStreamsConsumerStepSpecificationImpl::groupName).isEqualTo("group")
                prop(LettuceStreamsConsumerStepSpecificationImpl::streamKey).isEqualTo("test")

                prop(LettuceStreamsConsumerStepSpecificationImpl::monitoringConfig).all {
                    prop(StepMonitoringConfiguration::events).isTrue()
                    prop(StepMonitoringConfiguration::meters).isTrue()
                }
            }

    }

}