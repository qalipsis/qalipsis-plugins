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

package io.qalipsis.plugins.influxdb.configuration

import assertk.all
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import assertk.assertions.prop
import io.qalipsis.api.steps.StepMonitoringConfiguration
import io.qalipsis.plugins.influxdb.InfluxDbStepConnectionImpl
import io.qalipsis.plugins.influxdb.poll.InfluxDbPollStepSpecificationImpl
import io.qalipsis.plugins.influxdb.save.InfluxDbSaveStepSpecificationImpl
import io.qalipsis.plugins.influxdb.search.InfluxDbSearchStepSpecificationImpl
import org.junit.jupiter.api.Test

/**
 * @author Eric Jessé
 */
internal class InfluxDbDefaultsExtensionTest {

    @Test
    fun `should apply defaults to poll step`() {
        val defaults = InfluxDbDefaultsExtensionImpl()
        defaults.connect {
            server("http://my-server:8086", "my-bucket", "my-org")
            basic("my-user", "my-password")
            enableGzip()
        }
        defaults.monitoring {
            events = true
            meters = true
        }

        val spec = InfluxDbPollStepSpecificationImpl()
        defaults.applyTo(spec)

        assertThat(spec).all {
            prop(InfluxDbPollStepSpecificationImpl::connectionConfiguration).all {
                prop(InfluxDbStepConnectionImpl::url).isEqualTo("http://my-server:8086")
                prop(InfluxDbStepConnectionImpl::bucket).isEqualTo("my-bucket")
                prop(InfluxDbStepConnectionImpl::org).isEqualTo("my-org")
                prop(InfluxDbStepConnectionImpl::user).isEqualTo("my-user")
                prop(InfluxDbStepConnectionImpl::password).isEqualTo("my-password")
                prop(InfluxDbStepConnectionImpl::gzipEnabled).isTrue()
            }
            prop(InfluxDbPollStepSpecificationImpl::monitoringConfiguration).all {
                prop(StepMonitoringConfiguration::events).isTrue()
                prop(StepMonitoringConfiguration::meters).isTrue()
            }
        }
    }

    @Test
    fun `should apply defaults to save step`() {
        val defaults = InfluxDbDefaultsExtensionImpl()
        defaults.connect {
            server("http://my-server:8086", "my-bucket", "my-org")
            basic("my-user", "my-password")
        }
        defaults.monitoring {
            events = true
            meters = true
        }

        val spec = InfluxDbSaveStepSpecificationImpl<Any>()
        defaults.applyTo(spec)

        assertThat(spec).all {
            prop(InfluxDbSaveStepSpecificationImpl<*>::connectionConfig).all {
                prop(InfluxDbStepConnectionImpl::url).isEqualTo("http://my-server:8086")
                prop(InfluxDbStepConnectionImpl::bucket).isEqualTo("my-bucket")
                prop(InfluxDbStepConnectionImpl::org).isEqualTo("my-org")
                prop(InfluxDbStepConnectionImpl::user).isEqualTo("my-user")
                prop(InfluxDbStepConnectionImpl::password).isEqualTo("my-password")
                prop(InfluxDbStepConnectionImpl::gzipEnabled).isFalse()
            }
            prop(InfluxDbSaveStepSpecificationImpl<*>::monitoringConfig).all {
                prop(StepMonitoringConfiguration::events).isTrue()
                prop(StepMonitoringConfiguration::meters).isTrue()
            }
        }
    }

    @Test
    fun `should apply defaults to search step`() {
        val defaults = InfluxDbDefaultsExtensionImpl()
        defaults.connect {
            server("http://my-server:8086", "my-bucket", "my-org")
            basic("my-user", "my-password")
        }
        defaults.monitoring {
            events = true
            meters = true
        }

        val spec = InfluxDbSearchStepSpecificationImpl<Any>()
        defaults.applyTo(spec)

        assertThat(spec).all {
            prop(InfluxDbSearchStepSpecificationImpl<*>::connectionConfig).all {
                prop(InfluxDbStepConnectionImpl::url).isEqualTo("http://my-server:8086")
                prop(InfluxDbStepConnectionImpl::bucket).isEqualTo("my-bucket")
                prop(InfluxDbStepConnectionImpl::org).isEqualTo("my-org")
                prop(InfluxDbStepConnectionImpl::user).isEqualTo("my-user")
                prop(InfluxDbStepConnectionImpl::password).isEqualTo("my-password")
            }
            prop(InfluxDbSearchStepSpecificationImpl<*>::monitoringConfig).all {
                prop(StepMonitoringConfiguration::events).isTrue()
                prop(StepMonitoringConfiguration::meters).isTrue()
            }
        }
    }

    @Test
    fun `should not modify defaults when applying to different steps`() {
        val defaults = InfluxDbDefaultsExtensionImpl()
        defaults.connect {
            server("http://my-server:8086", "my-bucket", "my-org")
            basic("my-user", "my-password")
        }

        val spec1 = InfluxDbPollStepSpecificationImpl()
        defaults.applyTo(spec1)
        spec1.connectionConfiguration.server("http://other-server:8086", "other-bucket", "other-org")

        val spec2 = InfluxDbSaveStepSpecificationImpl<Any>()
        defaults.applyTo(spec2)

        // The second spec should still have the original defaults
        assertThat(spec2.connectionConfig).all {
            prop(InfluxDbStepConnectionImpl::url).isEqualTo("http://my-server:8086")
            prop(InfluxDbStepConnectionImpl::bucket).isEqualTo("my-bucket")
            prop(InfluxDbStepConnectionImpl::org).isEqualTo("my-org")
        }
    }
}
