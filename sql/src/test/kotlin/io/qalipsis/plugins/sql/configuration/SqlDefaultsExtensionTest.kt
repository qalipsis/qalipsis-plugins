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
