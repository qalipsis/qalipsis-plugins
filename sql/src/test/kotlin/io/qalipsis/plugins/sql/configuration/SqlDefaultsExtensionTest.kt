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

package io.qalipsis.plugins.sql.configuration

import assertk.all
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import assertk.assertions.isTrue
import assertk.assertions.prop
import io.qalipsis.api.steps.StepMonitoringConfiguration
import io.qalipsis.plugins.sql.SqlConnection
import io.qalipsis.plugins.sql.dialect.Protocol
import io.qalipsis.plugins.sql.poll.SqlPollStepSpecificationImpl
import io.qalipsis.plugins.sql.save.SqlSaveStepSpecificationImpl
import io.qalipsis.plugins.sql.search.SqlSearchStepSpecificationImpl
import org.junit.jupiter.api.Test

/**
 * @author Eric Jessé
 */
internal class SqlDefaultsExtensionTest {

    @Test
    fun `should apply defaults to poll step`() {
        val defaults = SqlDefaultsExtensionImpl()
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

        val spec = SqlPollStepSpecificationImpl()
        defaults.applyTo(spec)

        assertThat(spec).all {
            prop(SqlPollStepSpecificationImpl::connection).all {
                prop(SqlConnection::host).isEqualTo("default-host")
                prop(SqlConnection::port).isEqualTo(5432)
                prop(SqlConnection::database).isEqualTo("default-db")
                prop(SqlConnection::username).isEqualTo("default-user")
            }
            prop(SqlPollStepSpecificationImpl::protocol).isEqualTo(Protocol.POSTGRESQL)
            prop(SqlPollStepSpecificationImpl::monitoringConfig).all {
                prop(StepMonitoringConfiguration::events).isTrue()
                prop(StepMonitoringConfiguration::meters).isTrue()
            }
        }
    }

    @Test
    fun `should apply defaults to save step`() {
        val defaults = SqlDefaultsExtensionImpl()
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

        val spec = SqlSaveStepSpecificationImpl<Any>()
        defaults.applyTo(spec)

        assertThat(spec).all {
            prop(SqlSaveStepSpecificationImpl<*>::connection).all {
                prop(SqlConnection::host).isEqualTo("default-host")
                prop(SqlConnection::port).isEqualTo(3306)
                prop(SqlConnection::database).isEqualTo("default-db")
                prop(SqlConnection::username).isEqualTo("default-user")
            }
            prop(SqlSaveStepSpecificationImpl<*>::protocol).isEqualTo(Protocol.MYSQL)
            prop(SqlSaveStepSpecificationImpl<*>::monitoringConfig).all {
                prop(StepMonitoringConfiguration::events).isTrue()
                prop(StepMonitoringConfiguration::meters).isTrue()
            }
        }
    }

    @Test
    fun `should apply defaults to search step`() {
        val defaults = SqlDefaultsExtensionImpl()
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

        val spec = SqlSearchStepSpecificationImpl<Any>()
        defaults.applyTo(spec)

        assertThat(spec).all {
            prop(SqlSearchStepSpecificationImpl<*>::connection).all {
                prop(SqlConnection::host).isEqualTo("default-host")
                prop(SqlConnection::port).isEqualTo(5432)
                prop(SqlConnection::database).isEqualTo("default-db")
                prop(SqlConnection::username).isEqualTo("default-user")
            }
            prop(SqlSearchStepSpecificationImpl<*>::protocol).isEqualTo(Protocol.MARIADB)
            prop(SqlSearchStepSpecificationImpl<*>::monitoringConfig).all {
                prop(StepMonitoringConfiguration::events).isTrue()
                prop(StepMonitoringConfiguration::meters).isTrue()
            }
        }
    }

    @Test
    fun `should not apply protocol when not set in defaults`() {
        val defaults = SqlDefaultsExtensionImpl()
        defaults.connection {
            host = "default-host"
        }

        val spec = SqlPollStepSpecificationImpl()
        defaults.applyTo(spec)

        assertThat(spec).all {
            prop(SqlPollStepSpecificationImpl::connection).all {
                prop(SqlConnection::host).isEqualTo("default-host")
            }
            prop(SqlPollStepSpecificationImpl::protocol).isNull()
        }
    }
}
