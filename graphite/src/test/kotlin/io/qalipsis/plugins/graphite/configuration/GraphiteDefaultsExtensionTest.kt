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

package io.qalipsis.plugins.graphite.configuration

import assertk.all
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isTrue
import assertk.assertions.prop
import io.qalipsis.api.steps.StepMonitoringConfiguration
import io.qalipsis.plugins.graphite.GraphiteProtocol
import io.qalipsis.plugins.graphite.poll.GraphitePollStepSpecificationImpl
import io.qalipsis.plugins.graphite.save.GraphiteSaveStepSpecificationImpl
import org.junit.jupiter.api.Test

/**
 * @author Eric Jesse
 */
internal class GraphiteDefaultsExtensionTest {

    @Test
    fun `should apply defaults to poll step`() {
        val defaults = GraphiteDefaultsExtensionImpl()
        defaults.pollConnection {
            server("http://graphite:8080")
            basicAuthentication("user", "pass")
        }
        defaults.monitoring {
            events = true
            meters = true
        }

        val spec = GraphitePollStepSpecificationImpl()
        defaults.applyTo(spec)

        assertThat(spec).all {
            prop(GraphitePollStepSpecificationImpl::connectionConfiguration).all {
                prop("url") { it.url }.isEqualTo("http://graphite:8080")
                prop("username") { it.username }.isEqualTo("user")
                prop("password") { it.password }.isEqualTo("pass")
            }
            prop(GraphitePollStepSpecificationImpl::monitoringConfiguration).all {
                prop(StepMonitoringConfiguration::events).isTrue()
                prop(StepMonitoringConfiguration::meters).isTrue()
            }
        }
    }

    @Test
    fun `should apply defaults to save step`() {
        val defaults = GraphiteDefaultsExtensionImpl()
        defaults.saveConnection {
            server("graphite-host", 2004)
            protocol(GraphiteProtocol.PICKLE)
        }
        defaults.monitoring {
            events = true
            meters = true
        }

        val spec = GraphiteSaveStepSpecificationImpl<Any>()
        defaults.applyTo(spec)

        assertThat(spec).all {
            prop(GraphiteSaveStepSpecificationImpl<*>::connectionConfig).all {
                prop("host") { it.host }.isEqualTo("graphite-host")
                prop("port") { it.port }.isEqualTo(2004)
                prop("protocol") { it.protocol }.isEqualTo(GraphiteProtocol.PICKLE)
            }
            prop(GraphiteSaveStepSpecificationImpl<*>::monitoringConfig).all {
                prop(StepMonitoringConfiguration::events).isTrue()
                prop(StepMonitoringConfiguration::meters).isTrue()
            }
        }
    }

    @Test
    fun `should not apply poll connection when not set`() {
        val defaults = GraphiteDefaultsExtensionImpl()
        defaults.monitoring {
            events = true
        }

        val spec = GraphitePollStepSpecificationImpl()
        defaults.applyTo(spec)

        assertThat(spec).all {
            prop(GraphitePollStepSpecificationImpl::connectionConfiguration).all {
                prop("url") { it.url }.isEqualTo("http://127.0.0.1:8086")
            }
            prop(GraphitePollStepSpecificationImpl::monitoringConfiguration).all {
                prop(StepMonitoringConfiguration::events).isTrue()
            }
        }
    }

    @Test
    fun `should not apply save connection when not set`() {
        val defaults = GraphiteDefaultsExtensionImpl()
        defaults.monitoring {
            events = true
        }

        val spec = GraphiteSaveStepSpecificationImpl<Any>()
        defaults.applyTo(spec)

        assertThat(spec).all {
            prop(GraphiteSaveStepSpecificationImpl<*>::connectionConfig).all {
                prop("host") { it.host }.isEqualTo("localhost")
                prop("port") { it.port }.isEqualTo(2003)
            }
            prop(GraphiteSaveStepSpecificationImpl<*>::monitoringConfig).all {
                prop(StepMonitoringConfiguration::events).isTrue()
            }
        }
    }
}
