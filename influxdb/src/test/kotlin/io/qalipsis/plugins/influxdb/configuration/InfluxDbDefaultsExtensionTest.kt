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
