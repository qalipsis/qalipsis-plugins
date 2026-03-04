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

package io.qalipsis.plugins.redis.lettuce.configuration

import assertk.all
import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.isTrue
import assertk.assertions.prop
import io.qalipsis.api.scenario.ExtensibleScenarioSpecification
import io.qalipsis.api.scenario.StepSpecificationRegistry
import io.qalipsis.api.scenario.TestScenarioFactory
import io.qalipsis.api.steps.StepMonitoringConfiguration
import io.qalipsis.plugins.redis.lettuce.redisLettuce
import org.junit.jupiter.api.Test

/**
 * @author Eric Jessé
 */
internal class RedisLettuceDefaultsExtensionTest {

    @Test
    fun `should create defaults specification with connection and monitoring`() {
        val scenario = TestScenarioFactory.scenario("my-scenario", {
            redisLettuce().defaults {
                connection {
                    nodes = listOf("redis-host:6380", "redis-host:6381")
                    database = 2
                    redisConnectionType = RedisConnectionType.CLUSTER
                    authUser = "admin"
                    authPassword = "secret"
                    masterId = "mymaster"
                }
                monitoring {
                    events = true
                    meters = true
                }
            }
        })

        assertThat(scenario).all {
            isInstanceOf(StepSpecificationRegistry::class).prop(StepSpecificationRegistry::rootSteps).isEmpty()
            isInstanceOf(ExtensibleScenarioSpecification::class).all {
                prop(
                    "extension",
                    { it.getExtension<RedisLettuceDefaultsExtensionImpl>("redis-lettuce.defaults") }).isNotNull()
                    .all {
                        prop(RedisLettuceDefaultsExtensionImpl::connectionConfiguration).all {
                            prop(RedisConnectionConfiguration::nodes).isEqualTo(
                                listOf(
                                    "redis-host:6380",
                                    "redis-host:6381"
                                )
                            )
                            prop(RedisConnectionConfiguration::database).isEqualTo(2)
                            prop(RedisConnectionConfiguration::redisConnectionType).isEqualTo(RedisConnectionType.CLUSTER)
                            prop(RedisConnectionConfiguration::authUser).isEqualTo("admin")
                            prop(RedisConnectionConfiguration::authPassword).isEqualTo("secret")
                            prop(RedisConnectionConfiguration::masterId).isEqualTo("mymaster")
                        }
                        prop(RedisLettuceDefaultsExtensionImpl::monitoringConfig).all {
                            prop(StepMonitoringConfiguration::events).isTrue()
                            prop(StepMonitoringConfiguration::meters).isTrue()
                        }
                    }
            }
        }
    }

    @Test
    fun `should create defaults specification with connection only`() {
        val scenario = TestScenarioFactory.scenario("my-scenario", {
            redisLettuce().defaults {
                connection {
                    nodes = listOf("redis-host:6380")
                    database = 3
                }
            }
        })

        assertThat(scenario).all {
            isInstanceOf(StepSpecificationRegistry::class).prop(StepSpecificationRegistry::rootSteps).isEmpty()
            isInstanceOf(ExtensibleScenarioSpecification::class).all {
                prop(
                    "extension",
                    { it.getExtension<RedisLettuceDefaultsExtensionImpl>("redis-lettuce.defaults") }).isNotNull()
                    .all {
                        prop(RedisLettuceDefaultsExtensionImpl::connectionConfiguration).all {
                            prop(RedisConnectionConfiguration::nodes).isEqualTo(listOf("redis-host:6380"))
                            prop(RedisConnectionConfiguration::database).isEqualTo(3)
                        }
                        prop(RedisLettuceDefaultsExtensionImpl::monitoringConfig).all {
                            prop(StepMonitoringConfiguration::events).isFalse()
                            prop(StepMonitoringConfiguration::meters).isFalse()
                        }
                    }
            }
        }
    }

    @Test
    fun `should create defaults specification with monitoring only`() {
        val scenario = TestScenarioFactory.scenario("my-scenario", {
            redisLettuce().defaults {
                monitoring {
                    events = true
                    meters = true
                }
            }
        })

        assertThat(scenario).all {
            isInstanceOf(StepSpecificationRegistry::class).prop(StepSpecificationRegistry::rootSteps).isEmpty()
            isInstanceOf(ExtensibleScenarioSpecification::class).all {
                prop(
                    "extension",
                    { it.getExtension<RedisLettuceDefaultsExtensionImpl>("redis-lettuce.defaults") }).isNotNull()
                    .all {
                        prop(RedisLettuceDefaultsExtensionImpl::connectionConfiguration).all {
                            prop(RedisConnectionConfiguration::nodes).isEqualTo(listOf("localhost:6379"))
                            prop(RedisConnectionConfiguration::database).isEqualTo(0)
                            prop(RedisConnectionConfiguration::authUser).isEqualTo("")
                            prop(RedisConnectionConfiguration::authPassword).isEqualTo("")
                        }
                        prop(RedisLettuceDefaultsExtensionImpl::monitoringConfig).all {
                            prop(StepMonitoringConfiguration::events).isTrue()
                            prop(StepMonitoringConfiguration::meters).isTrue()
                        }
                    }
            }
        }
    }
}
