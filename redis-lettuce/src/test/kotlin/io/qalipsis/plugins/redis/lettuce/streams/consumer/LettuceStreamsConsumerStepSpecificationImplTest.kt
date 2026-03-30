/*
 * QALIPSIS
 * Copyright (C) 2025 AERIS IT Solutions GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package io.qalipsis.plugins.redis.lettuce.streams.consumer

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
import io.qalipsis.plugins.redis.lettuce.configuration.RedisConnectionConfiguration
import io.qalipsis.plugins.redis.lettuce.configuration.RedisConnectionType
import io.qalipsis.plugins.redis.lettuce.configuration.defaults
import io.qalipsis.plugins.redis.lettuce.redisLettuce
import io.qalipsis.test.coroutines.TestDispatcherProvider
import java.time.Duration
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

internal class LettuceStreamsConsumerStepSpecificationImplTest {

    @JvmField
    @RegisterExtension
    val testDispatcherProvider = TestDispatcherProvider()

    @Test
    internal fun `should add minimal specification to the scenario with default values`() =
        testDispatcherProvider.runTest {
            val scenario = TestScenarioFactory.scenario("my-scenario") as StepSpecificationRegistry
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
                    prop(StepMonitoringConfiguration::events).isTrue()
                    prop(StepMonitoringConfiguration::meters).isTrue()
                }
            }
    }

    @Test
    internal fun `should add a complete specification to the scenario as broadcast`() = testDispatcherProvider.runTest {

        val scenario = TestScenarioFactory.scenario("my-scenario") as StepSpecificationRegistry
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

    @Test
    internal fun `should apply defaults from RedisLettuceDefaultsExtension`() = testDispatcherProvider.runTest {
        val scenario = TestScenarioFactory.scenario("my-scenario", {
            redisLettuce().defaults {
                connection {
                    nodes = listOf("default-host:6380", "default-host:6381")
                    database = 5
                    redisConnectionType = RedisConnectionType.CLUSTER
                    authUser = "admin"
                    authPassword = "secret"
                }
                monitoring {
                    events = true
                    meters = true
                }
            }
        })

        scenario.start().redisLettuce().streamsConsume {
            name = "my-step"
            streamKey("test")
            group("group")
        }

        assertThat((scenario as StepSpecificationRegistry).rootSteps.first()).isInstanceOf(
            LettuceStreamsConsumerStepSpecificationImpl::class
        ).all {
            prop(LettuceStreamsConsumerStepSpecificationImpl::connection).all {
                prop(RedisConnectionConfiguration::nodes).isEqualTo(listOf("default-host:6380", "default-host:6381"))
                prop(RedisConnectionConfiguration::database).isEqualTo(5)
                prop(RedisConnectionConfiguration::redisConnectionType).isEqualTo(RedisConnectionType.CLUSTER)
                prop(RedisConnectionConfiguration::authUser).isEqualTo("admin")
                prop(RedisConnectionConfiguration::authPassword).isEqualTo("secret")
            }
            prop(LettuceStreamsConsumerStepSpecificationImpl::monitoringConfig).all {
                prop(StepMonitoringConfiguration::events).isTrue()
                prop(StepMonitoringConfiguration::meters).isTrue()
            }
        }
    }

    @Test
    internal fun `should allow overriding defaults from RedisLettuceDefaultsExtension`() =
        testDispatcherProvider.runTest {
            val scenario = TestScenarioFactory.scenario("my-scenario", {
                redisLettuce().defaults {
                    connection {
                        nodes = listOf("default-host:6380")
                        database = 5
                        authUser = "admin"
                        authPassword = "secret"
                    }
                    monitoring {
                        events = true
                        meters = true
                    }
                }
            })

            scenario.start().redisLettuce().streamsConsume {
                name = "my-step"
                streamKey("test")
                group("group")
                connection {
                    nodes = listOf("override-host:6379")
                }
                monitoring {
                    events = false
                    // meters not set -> inherits true from defaults
                }
            }

            assertThat((scenario as StepSpecificationRegistry).rootSteps.first()).isInstanceOf(
                LettuceStreamsConsumerStepSpecificationImpl::class
            ).all {
                prop(LettuceStreamsConsumerStepSpecificationImpl::connection).all {
                    prop(RedisConnectionConfiguration::nodes).isEqualTo(listOf("override-host:6379"))
                    prop(RedisConnectionConfiguration::database).isEqualTo(5)
                    prop(RedisConnectionConfiguration::authUser).isEqualTo("admin")
                    prop(RedisConnectionConfiguration::authPassword).isEqualTo("secret")
                }
                prop(LettuceStreamsConsumerStepSpecificationImpl::monitoringConfig).all {
                    prop(StepMonitoringConfiguration::events).isFalse()
                    prop(StepMonitoringConfiguration::meters).isTrue()
                }
            }
    }

}