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

package io.qalipsis.plugins.influxdb.config

import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isNotEmpty
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.influx.InfluxMeterRegistry
import io.micronaut.context.ApplicationContext
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import io.micronaut.test.support.TestPropertyProvider
import jakarta.inject.Inject
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

internal class InfluxDbMeterRegistryFactoryIntegrationTest {

    @Nested
    @MicronautTest(startApplication = false, environments = ["influxdb"])
    inner class WithoutMeters : TestPropertyProvider {

        @Inject
        private lateinit var applicationContext: ApplicationContext

        override fun getProperties(): MutableMap<String, String> {
            return mutableMapOf(
                "meters.export.enabled" to "false",
                "meters.export.influxdb.enabled" to "true"
            )
        }

        @Test
        @Timeout(10)
        internal fun `should start without influxdb meter registry`() {
            assertThat(applicationContext.getBeansOfType(MeterRegistry::class.java)).isNotEmpty()
            assertThat(applicationContext.getBeansOfType(InfluxMeterRegistry::class.java)).isEmpty()
        }
    }

    @Nested
    @MicronautTest(startApplication = false, environments = ["influxdb"])
    inner class WithMetersButWithoutInfluxdb : TestPropertyProvider {

        @Inject
        private lateinit var applicationContext: ApplicationContext

        override fun getProperties(): MutableMap<String, String> {
            return mutableMapOf(
                "meters.export.enabled" to "true",
                "meters.export.influxdb.enabled" to "false"
            )
        }

        @Test
        @Timeout(10)
        internal fun `should start without influxdb meter registry`() {
            assertThat(applicationContext.getBeansOfType(MeterRegistry::class.java)).isNotEmpty()
            assertThat(applicationContext.getBeansOfType(InfluxMeterRegistry::class.java)).isEmpty()
        }
    }

    @Nested
    @MicronautTest(startApplication = false, environments = ["influxdb"])
    inner class WithInfluxdbMeterRegistry : TestPropertyProvider {

        @Inject
        private lateinit var applicationContext: ApplicationContext

        override fun getProperties(): MutableMap<String, String> {
            return mutableMapOf(
                "meters.export.enabled" to "true",
                "meters.export.influxdb.enabled" to "true"
            )
        }

        @Test
        @Timeout(10)
        internal fun `should start with influxdb meter registry`() {
            assertThat(applicationContext.getBeansOfType(MeterRegistry::class.java)).isNotEmpty()
            assertThat(applicationContext.getBeansOfType(InfluxMeterRegistry::class.java)).hasSize(1)
        }
    }
}