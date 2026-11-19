/*
 * QALIPSIS
 * Copyright (C) 2025 AERIS IT Solutions GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
