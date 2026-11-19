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

package io.qalipsis.plugins.r2dbc.jasync.configuration

import assertk.all
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import assertk.assertions.isTrue
import assertk.assertions.prop
import io.qalipsis.api.steps.StepMonitoringConfiguration
import io.qalipsis.plugins.r2dbc.jasync.JasyncConnection
import io.qalipsis.plugins.r2dbc.jasync.dialect.Protocol
import io.qalipsis.plugins.r2dbc.jasync.poll.JasyncPollStepSpecificationImpl
import io.qalipsis.plugins.r2dbc.jasync.save.JasyncSaveStepSpecificationImpl
import io.qalipsis.plugins.r2dbc.jasync.search.JasyncSearchStepSpecificationImpl
import org.junit.jupiter.api.Test

/**
 * @author Eric Jessé
 */
internal class R2dbcJasyncDefaultsExtensionTest {

    @Test
    fun `should apply defaults to poll step`() {
        val defaults = R2dbcJasyncDefaultsExtensionImpl()
        defaults.connection {
            host = "default-host"
            port = 5432
            database = "default-db"
            username = "default-user"
        }
        defaults.protocol(Protocol.POSTGRESQL)
        defaults.monitoring {
            events = true
            meters = true
        }

        val spec = JasyncPollStepSpecificationImpl()
        defaults.applyTo(spec)

        assertThat(spec).all {
            prop(JasyncPollStepSpecificationImpl::connection).all {
                prop(JasyncConnection::host).isEqualTo("default-host")
                prop(JasyncConnection::port).isEqualTo(5432)
                prop(JasyncConnection::database).isEqualTo("default-db")
                prop(JasyncConnection::username).isEqualTo("default-user")
            }
            prop(JasyncPollStepSpecificationImpl::protocol).isEqualTo(Protocol.POSTGRESQL)
            prop(JasyncPollStepSpecificationImpl::monitoringConfig).all {
                prop(StepMonitoringConfiguration::events).isTrue()
                prop(StepMonitoringConfiguration::meters).isTrue()
            }
        }
    }

    @Test
    fun `should apply defaults to save step`() {
        val defaults = R2dbcJasyncDefaultsExtensionImpl()
        defaults.connection {
            host = "default-host"
            port = 3306
            database = "default-db"
            username = "default-user"
        }
        defaults.protocol(Protocol.MYSQL)
        defaults.monitoring {
            events = true
            meters = true
        }

        val spec = JasyncSaveStepSpecificationImpl<Any>()
        defaults.applyTo(spec)

        assertThat(spec).all {
            prop(JasyncSaveStepSpecificationImpl<*>::connection).all {
                prop(JasyncConnection::host).isEqualTo("default-host")
                prop(JasyncConnection::port).isEqualTo(3306)
                prop(JasyncConnection::database).isEqualTo("default-db")
                prop(JasyncConnection::username).isEqualTo("default-user")
            }
            prop(JasyncSaveStepSpecificationImpl<*>::protocol).isEqualTo(Protocol.MYSQL)
            prop(JasyncSaveStepSpecificationImpl<*>::monitoringConfig).all {
                prop(StepMonitoringConfiguration::events).isTrue()
                prop(StepMonitoringConfiguration::meters).isTrue()
            }
        }
    }

    @Test
    fun `should apply defaults to search step`() {
        val defaults = R2dbcJasyncDefaultsExtensionImpl()
        defaults.connection {
            host = "default-host"
            port = 5432
            database = "default-db"
            username = "default-user"
        }
        defaults.protocol(Protocol.MARIADB)
        defaults.monitoring {
            events = true
            meters = true
        }

        val spec = JasyncSearchStepSpecificationImpl<Any>()
        defaults.applyTo(spec)

        assertThat(spec).all {
            prop(JasyncSearchStepSpecificationImpl<*>::connection).all {
                prop(JasyncConnection::host).isEqualTo("default-host")
                prop(JasyncConnection::port).isEqualTo(5432)
                prop(JasyncConnection::database).isEqualTo("default-db")
                prop(JasyncConnection::username).isEqualTo("default-user")
            }
            prop(JasyncSearchStepSpecificationImpl<*>::protocol).isEqualTo(Protocol.MARIADB)
            prop(JasyncSearchStepSpecificationImpl<*>::monitoringConfig).all {
                prop(StepMonitoringConfiguration::events).isTrue()
                prop(StepMonitoringConfiguration::meters).isTrue()
            }
        }
    }

    @Test
    fun `should not apply protocol when not set in defaults`() {
        val defaults = R2dbcJasyncDefaultsExtensionImpl()
        defaults.connection {
            host = "default-host"
        }

        val spec = JasyncPollStepSpecificationImpl()
        defaults.applyTo(spec)

        assertThat(spec).all {
            prop(JasyncPollStepSpecificationImpl::connection).all {
                prop(JasyncConnection::host).isEqualTo("default-host")
            }
            prop(JasyncPollStepSpecificationImpl::protocol).isNull()
        }
    }
}
