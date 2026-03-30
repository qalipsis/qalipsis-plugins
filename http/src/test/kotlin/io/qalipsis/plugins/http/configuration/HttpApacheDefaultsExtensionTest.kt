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

package io.qalipsis.plugins.http.configuration

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
import io.qalipsis.plugins.http.ConnectionStrategyConfiguration
import io.qalipsis.plugins.http.ConnectionStrategyType
import io.qalipsis.plugins.http.HttpClientConfiguration
import io.qalipsis.plugins.http.httpApache
import org.apache.hc.core5.http.HttpVersion
import org.junit.jupiter.api.Test

/**
 * @author Eric Jessé
 */
internal class HttpApacheDefaultsExtensionTest {

    @Test
    fun `should create defaults specification with connection and monitoring`() {
        // when
        val scenario = TestScenarioFactory.scenario("my-scenario", {
            httpApache().defaults {
                connect {
                    url("https://localhost:8443/api")
                    version = HttpVersion.HTTP_2
                }
                monitoring {
                    events = true
                    meters = true
                }
            }
        })

        // then
        assertThat(scenario).all {
            isInstanceOf(StepSpecificationRegistry::class).prop(StepSpecificationRegistry::rootSteps).isEmpty()
            isInstanceOf(ExtensibleScenarioSpecification::class).all {
                prop("extension", { it.getExtension<HttpApacheDefaultsExtensionImpl>(EXTENSION_NAME) }).isNotNull()
                    .all {
                        prop(HttpApacheDefaultsExtensionImpl::connectionConfiguration).all {
                            prop(HttpClientConfiguration::host).isEqualTo("localhost")
                            prop(HttpClientConfiguration::port).isEqualTo(8443)
                            prop(HttpClientConfiguration::scheme).isEqualTo("https")
                            prop(HttpClientConfiguration::contextPath).isEqualTo("/api")
                            prop(HttpClientConfiguration::version).isEqualTo(HttpVersion.HTTP_2)
                        }
                        prop(HttpApacheDefaultsExtensionImpl::monitoringConfig).all {
                            prop(StepMonitoringConfiguration::events).isTrue()
                            prop(StepMonitoringConfiguration::meters).isTrue()
                        }
                    }
            }
        }
    }

    @Test
    fun `should create defaults specification with connection only`() {
        // when
        val scenario = TestScenarioFactory.scenario("my-scenario", {
            httpApache().defaults {
                connect {
                    url("http://localhost:9090")
                }
            }
        })

        // then
        assertThat(scenario).all {
            isInstanceOf(StepSpecificationRegistry::class).prop(StepSpecificationRegistry::rootSteps).isEmpty()
            isInstanceOf(ExtensibleScenarioSpecification::class).all {
                prop("extension", { it.getExtension<HttpApacheDefaultsExtensionImpl>(EXTENSION_NAME) }).isNotNull()
                    .all {
                        prop(HttpApacheDefaultsExtensionImpl::connectionConfiguration).all {
                            prop(HttpClientConfiguration::host).isEqualTo("localhost")
                            prop(HttpClientConfiguration::port).isEqualTo(9090)
                            prop(HttpClientConfiguration::scheme).isEqualTo("http")
                        }
                        prop(HttpApacheDefaultsExtensionImpl::monitoringConfig).all {
                            prop(StepMonitoringConfiguration::events).isTrue()
                            prop(StepMonitoringConfiguration::meters).isTrue()
                        }
                    }
            }
        }
    }

    @Test
    fun `should create defaults specification with monitoring only`() {
        // when
        val scenario = TestScenarioFactory.scenario("my-scenario", {
            httpApache().defaults {
                monitoring {
                    events = false
                    meters = false
                }
            }
        })

        // then
        assertThat(scenario).all {
            isInstanceOf(StepSpecificationRegistry::class).prop(StepSpecificationRegistry::rootSteps).isEmpty()
            isInstanceOf(ExtensibleScenarioSpecification::class).all {
                prop("extension", { it.getExtension<HttpApacheDefaultsExtensionImpl>(EXTENSION_NAME) }).isNotNull()
                    .all {
                        prop(HttpApacheDefaultsExtensionImpl::connectionConfiguration).all {
                            prop(HttpClientConfiguration::host).isEqualTo("localhost")
                            prop(HttpClientConfiguration::port).isEqualTo(80)
                            prop(HttpClientConfiguration::scheme).isEqualTo("http")
                        }
                        prop(HttpApacheDefaultsExtensionImpl::monitoringConfig).all {
                            prop(StepMonitoringConfiguration::events).isFalse()
                            prop(StepMonitoringConfiguration::meters).isFalse()
                        }
                    }
            }
        }
    }

    @Test
    fun `should create defaults specification with connection strategy`() {
        // when
        val scenario = TestScenarioFactory.scenario("my-scenario", {
            httpApache().defaults {
                connect {
                    url("http://localhost:8080")
                    connectionStrategy {
                        shared = true
                        strategyType = ConnectionStrategyType.POOL
                    }
                }
            }
        })

        // then
        assertThat(scenario).all {
            isInstanceOf(StepSpecificationRegistry::class).prop(StepSpecificationRegistry::rootSteps).isEmpty()
            isInstanceOf(ExtensibleScenarioSpecification::class).all {
                prop("extension", { it.getExtension<HttpApacheDefaultsExtensionImpl>(EXTENSION_NAME) }).isNotNull()
                    .all {
                        prop(HttpApacheDefaultsExtensionImpl::connectionConfiguration).all {
                            prop(HttpClientConfiguration::host).isEqualTo("localhost")
                            prop(HttpClientConfiguration::port).isEqualTo(8080)
                            prop(HttpClientConfiguration::connectionStrategyConfiguration).all {
                                prop(ConnectionStrategyConfiguration::shared).isTrue()
                                prop(ConnectionStrategyConfiguration::strategyType).isEqualTo(ConnectionStrategyType.POOL)
                            }
                        }
                    }
            }
        }
    }
}
