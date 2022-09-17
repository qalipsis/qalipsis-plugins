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

package io.qalipsis.plugins.influxdb.events

import assertk.all
import assertk.assertThat
import assertk.assertions.any
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.prop
import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Property
import io.micronaut.core.util.StringUtils
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import io.qalipsis.api.events.EventLevel
import io.qalipsis.api.events.EventsPublisher
import jakarta.inject.Inject
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.time.Duration

/**
 * Tests to verify the automatic configuration of the IfluxDb publisher.
 */
class InfluxDbEventsPublisherConfigurationIntegrationTest {

    @Nested
    @MicronautTest(propertySources = ["classpath:application-influxdb.yml"])
    inner class NoPublisher {

        @Inject
        private lateinit var applicationContext: ApplicationContext

        @Test
        @Timeout(10)
        internal fun `should start without publisher`() {
            assertThat(applicationContext.getBeansOfType(EventsPublisher::class.java)).isEmpty()
            assertThat(applicationContext.getBeansOfType(InfluxDbEventsConfiguration::class.java)).isEmpty()
        }
    }

    @Nested
    @MicronautTest(propertySources = ["classpath:application-influxdb.yml"])
    @Property(name = "events.export.influxdb.enabled", value = StringUtils.TRUE)
    inner class WithPublisher {

        @Inject
        private lateinit var applicationContext: ApplicationContext

        @Test
        @Timeout(10)
        internal fun `should start with the publisher`() {
            assertThat(applicationContext.getBeansOfType(EventsPublisher::class.java)).all {
                hasSize(1)
                any { it.isInstanceOf(InfluxDbEventsPublisher::class) }
            }
            val configuration = applicationContext.getBean(InfluxDbEventsConfiguration::class.java)
            assertThat(configuration).all {
                prop(InfluxDbEventsConfiguration::minLevel).isEqualTo(EventLevel.INFO)
                prop(InfluxDbEventsConfiguration::lingerPeriod).isEqualTo(Duration.ofSeconds(10))
                prop(InfluxDbEventsConfiguration::batchSize).isEqualTo(2000)
                prop(InfluxDbEventsConfiguration::publishers).isEqualTo(1)
                prop(InfluxDbEventsConfiguration::username).isEqualTo("user")
                prop(InfluxDbEventsConfiguration::password).isEqualTo("passpasspass")
                prop(InfluxDbEventsConfiguration::org).isEqualTo("qalipsis")
                prop(InfluxDbEventsConfiguration::bucket).isEqualTo("qalipsis-event")
                prop(InfluxDbEventsConfiguration::url).isEqualTo("http://localhost:8086")
            }
        }
    }
}
