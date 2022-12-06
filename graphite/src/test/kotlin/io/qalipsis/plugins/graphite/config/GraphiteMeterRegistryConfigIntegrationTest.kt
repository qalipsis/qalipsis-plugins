/*
 * Copyright 2022 AERIS IT Solutions GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package io.qalipsis.plugins.graphite.config

import assertk.all
import assertk.assertThat
import assertk.assertions.any
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.prop
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.graphite.GraphiteConfig
import io.micrometer.graphite.GraphiteMeterRegistry
import io.micronaut.context.ApplicationContext
import io.micronaut.core.util.StringUtils
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import io.micronaut.test.support.TestPropertyProvider
import io.qalipsis.api.meters.MeterRegistryConfiguration
import io.qalipsis.api.meters.MeterRegistryFactory
import io.qalipsis.test.assertk.typedProp
import jakarta.inject.Inject
import java.time.Duration
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

internal class GraphiteMeterRegistryConfigIntegrationTest {

    @Nested
    @MicronautTest(environments = ["graphite"], startApplication = false)
    inner class WithoutRegistry {

        @Inject
        private lateinit var applicationContext: ApplicationContext

        @Test
        @Timeout(4)
        fun `should start without the registry`() {
            assertThat(applicationContext.getBeansOfType(GraphiteMeterRegistry::class.java)).isEmpty()
            assertThat(applicationContext.getBeansOfType(MeterRegistryFactory::class.java)).isEmpty()
        }
    }

    @Nested
    @MicronautTest(environments = ["graphite"], startApplication = false)
    inner class WithConfiguredRegistry : TestPropertyProvider {

        @Inject
        private lateinit var applicationContext: ApplicationContext

        @Test
        @Timeout(4)
        internal fun `should start with the configured registry`() {
            assertThat(applicationContext.getBeansOfType(MeterRegistry::class.java)).any {
                it.isInstanceOf(GraphiteMeterRegistry::class)
            }

            assertThat(applicationContext.getBean(GraphiteMeterRegistry::class.java)).typedProp<GraphiteConfig>("config").all {
                prop(GraphiteConfig::prefix).isEqualTo("graphite")
                prop(GraphiteConfig::host).isEqualTo("graphite-host")
                prop(GraphiteConfig::port).isEqualTo(1234)
                prop(GraphiteConfig::step).isEqualTo(Duration.ofMinutes(6))
            }
        }

        override fun getProperties(): Map<String, String> {
            return mapOf(
                "meters.export.graphite.enabled" to StringUtils.TRUE,
                "meters.export.graphite.host" to "graphite-host",
                "meters.export.graphite.port" to "1234",
                "meters.export.graphite.step" to "PT6M",
            )
        }
    }

    @Nested
    @MicronautTest(environments = ["graphite"], startApplication = false)
    inner class WithRegistry : TestPropertyProvider{

        @Inject
        private lateinit var applicationContext: ApplicationContext

        @Test
        @Timeout(4)
        internal fun `should start with the registry`() {
            assertThat(applicationContext.getBeansOfType(MeterRegistry::class.java)).any {
                it.isInstanceOf(GraphiteMeterRegistry::class)
            }

            assertThat(applicationContext.getBean(GraphiteMeterRegistry::class.java)).typedProp<GraphiteConfig>("config").all {
                prop(GraphiteConfig::prefix).isEqualTo("graphite")
                prop(GraphiteConfig::host).isEqualTo("localhost")
                prop(GraphiteConfig::port).isEqualTo(2004)
                prop(GraphiteConfig::step).isEqualTo(Duration.ofSeconds(10))
            }

            val meterRegistryFactory = applicationContext.getBean(MeterRegistryFactory::class.java)
            var generatedMeterRegistry = meterRegistryFactory.getRegistry(
                object : MeterRegistryConfiguration {
                    override val step: Duration? = null
                }
            )
            assertThat(generatedMeterRegistry).typedProp<GraphiteConfig>("config").all {
                prop(GraphiteConfig::prefix).isEqualTo("graphite")
                prop(GraphiteConfig::host).isEqualTo("localhost")
                prop(GraphiteConfig::port).isEqualTo(2004)
                prop(GraphiteConfig::step).isEqualTo(Duration.ofSeconds(10))
            }

            generatedMeterRegistry = meterRegistryFactory.getRegistry(
                object : MeterRegistryConfiguration {
                    override val step: Duration = Duration.ofSeconds(3)

                }
            )
            assertThat(generatedMeterRegistry).typedProp<GraphiteConfig>("config").all {
                prop(GraphiteConfig::prefix).isEqualTo("graphite")
                prop(GraphiteConfig::host).isEqualTo("localhost")
                prop(GraphiteConfig::port).isEqualTo(2004)
                prop(GraphiteConfig::step).isEqualTo(Duration.ofSeconds(3))
            }
        }

        override fun getProperties(): Map<String, String> {
            return mapOf(
                "meters.export.graphite.enabled" to StringUtils.TRUE,
            )
        }
    }
}