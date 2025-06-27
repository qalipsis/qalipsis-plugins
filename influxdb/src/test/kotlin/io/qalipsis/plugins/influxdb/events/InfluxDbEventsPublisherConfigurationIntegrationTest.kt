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
 * Tests to verify the automatic configuration of the InfluxDb publisher.
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
