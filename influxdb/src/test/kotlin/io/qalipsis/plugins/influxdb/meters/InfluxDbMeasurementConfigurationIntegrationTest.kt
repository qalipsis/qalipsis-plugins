/*
 * Copyright 2024 AERIS IT Solutions GmbH
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

package io.qalipsis.plugins.influxdb.meters

import assertk.all
import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.prop
import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Property
import io.micronaut.core.util.StringUtils
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import io.qalipsis.api.meters.MeasurementPublisherFactory
import io.qalipsis.plugins.influxdb.config.InfluxDbMeasurementPublisherFactory
import jakarta.inject.Inject
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.testcontainers.shaded.org.hamcrest.Matchers.hasSize

/**
 * Tests to verify the configuration of the InfluxDb measurement publisher.
 */
class InfluxDbMeasurementConfigurationIntegrationTest {

    @Nested
    @MicronautTest(propertySources = ["classpath:application-influxdb.yml"])
    inner class NoPublisher {

        @Inject
        private lateinit var applicationContext: ApplicationContext

        @Test
        @Timeout(10)
        internal fun `should start without publisher`() {
            assertThat(applicationContext.getBeansOfType(MeasurementPublisherFactory::class.java)).isEmpty()
            assertThat(applicationContext.getBeansOfType(InfluxDbMeasurementConfiguration::class.java)).isEmpty()
        }
    }

    @Nested
    @MicronautTest(propertySources = ["classpath:application-influxdb.yml"])
    @Property(name = "meters.export.influxdb.enabled", value = StringUtils.TRUE)
    inner class WithPublisher {

        @Inject
        private lateinit var applicationContext: ApplicationContext

        @Test
        @Timeout(10)
        internal fun `should start with the publisher`() {
            assertThat(applicationContext.getBeansOfType(MeasurementPublisherFactory::class.java).forEach {
                assertThat(it.getPublisher()).all {
                    isInstanceOf(InfluxdbMeasurementPublisher::class.java)
                }
            }).all { hasSize<InfluxDbMeasurementPublisherFactory>(1) }
            val configuration = applicationContext.getBean(InfluxDbMeasurementConfiguration::class.java)
            assertThat(configuration).all {
                prop(InfluxDbMeasurementConfiguration::publishers).isEqualTo(1)
                prop(InfluxDbMeasurementConfiguration::userName).isEqualTo("")
                prop(InfluxDbMeasurementConfiguration::password).isEqualTo("")
                prop(InfluxDbMeasurementConfiguration::org).isEqualTo("qalipsis")
                prop(InfluxDbMeasurementConfiguration::bucket).isEqualTo("qalipsis-meter")
                prop(InfluxDbMeasurementConfiguration::url).isEqualTo("http://localhost:8086")
            }
        }
    }
}
