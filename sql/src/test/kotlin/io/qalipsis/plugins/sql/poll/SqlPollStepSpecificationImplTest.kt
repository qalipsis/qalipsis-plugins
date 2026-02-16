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

package io.qalipsis.plugins.sql.poll

import assertk.all
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isNull
import assertk.assertions.isTrue
import assertk.assertions.prop
import io.qalipsis.api.scenario.StepSpecificationRegistry
import io.qalipsis.api.scenario.TestScenarioFactory
import io.qalipsis.api.steps.SingletonConfiguration
import io.qalipsis.api.steps.SingletonType
import io.qalipsis.api.steps.StepMonitoringConfiguration
import io.qalipsis.plugins.sql.SqlConnection
import io.qalipsis.plugins.sql.dialect.Protocol
import io.qalipsis.plugins.sql.sql
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.LocalDate

/**
 *
 * @author Eric Jesse
 */
internal class SqlPollStepSpecificationImplTest {

    @Test
    internal fun `should add minimal specification to the scenario`() {
        val scenario = TestScenarioFactory.scenario("my-scenario") as StepSpecificationRegistry
        scenario.sql().poll {
            name = "my-step"
            protocol(Protocol.POSTGRESQL)
            connection {
                port = 1234
                database = "my-database"
                username = "my-username"
            }
            query("This is my query")
            parameters(123, "my-param", true, LocalDate.of(2020, 11, 13))
            pollDelay(Duration.ofSeconds(12))
        }

        assertThat(scenario.rootSteps[0]).isInstanceOf(SqlPollStepSpecificationImpl::class).all {
            prop(SqlPollStepSpecificationImpl::name).isEqualTo("my-step")
            prop(SqlPollStepSpecificationImpl::protocol).isEqualTo(Protocol.POSTGRESQL)
            prop(SqlPollStepSpecificationImpl::connection).all {
                prop(SqlConnection::host).isEqualTo("localhost")
                prop(SqlConnection::port).isEqualTo(1234)
                prop(SqlConnection::database).isEqualTo("my-database")
                prop(SqlConnection::username).isEqualTo("my-username")
                prop(SqlConnection::password).isNull()
                prop(SqlConnection::maxSize).isEqualTo(1)
                prop(SqlConnection::maxIdleTime).isEqualTo(Duration.ofMinutes(1))
                prop(SqlConnection::maxCreateConnectionTime).isEqualTo(Duration.ofMillis(5000))
                prop(SqlConnection::applicationName).isEqualTo("sql")
                prop(SqlConnection::options).isEqualTo(emptyMap())
            }
            prop(SqlPollStepSpecificationImpl::query).isEqualTo("This is my query")
            prop(SqlPollStepSpecificationImpl::parameters).all {
                hasSize(4)
                containsExactly(123, "my-param", true, LocalDate.of(2020, 11, 13))
            }
            prop(SqlPollStepSpecificationImpl::pollDelay).isEqualTo(Duration.ofSeconds(12))
            prop(SqlPollStepSpecificationImpl::monitoringConfig).all {
                prop(StepMonitoringConfiguration::meters).isFalse()
                prop(StepMonitoringConfiguration::events).isFalse()
            }
            prop(SqlPollStepSpecificationImpl::singletonConfiguration).all {
                prop(SingletonConfiguration::type).isEqualTo(SingletonType.UNICAST)
                prop(SingletonConfiguration::bufferSize).isEqualTo(-1)
                prop(SingletonConfiguration::idleTimeout).isEqualTo(Duration.ZERO)
            }
        }
    }


    @Test
    internal fun `should add a complete specification to the scenario as broadcast`() {
        val scenario = TestScenarioFactory.scenario("my-scenario") as StepSpecificationRegistry
        scenario.sql().poll {
            name = "my-other-step"
            protocol(Protocol.MARIADB)
            connection {
                host = "my-server"
                port = 5678
                database = "my-other-database"
                username = "my-other-username"
                password = "my-other-password"
                maxSize = 10
                maxIdleTime = Duration.ofMinutes(5)
                maxCreateConnectionTime = Duration.ofSeconds(10)
                applicationName = "my-app"
                options = mapOf("key1" to "value1")
            }
            query("This is my other query")
            parameters()
            pollDelay(Duration.ofSeconds(23))
            monitoring {
                meters = true
                events = false
            }
            broadcast(123, Duration.ofSeconds(20))
        }

        assertThat(scenario.rootSteps[0]).isInstanceOf(SqlPollStepSpecificationImpl::class).all {
            prop(SqlPollStepSpecificationImpl::name).isEqualTo("my-other-step")
            prop(SqlPollStepSpecificationImpl::protocol).isEqualTo(Protocol.MARIADB)
            prop(SqlPollStepSpecificationImpl::connection).all {
                prop(SqlConnection::host).isEqualTo("my-server")
                prop(SqlConnection::port).isEqualTo(5678)
                prop(SqlConnection::database).isEqualTo("my-other-database")
                prop(SqlConnection::username).isEqualTo("my-other-username")
                prop(SqlConnection::password).isEqualTo("my-other-password")
                prop(SqlConnection::maxSize).isEqualTo(10)
                prop(SqlConnection::maxIdleTime).isEqualTo(Duration.ofMinutes(5))
                prop(SqlConnection::maxCreateConnectionTime).isEqualTo(Duration.ofSeconds(10))
                prop(SqlConnection::applicationName).isEqualTo("my-app")
                prop(SqlConnection::options).isEqualTo(mapOf("key1" to "value1"))
            }
            prop(SqlPollStepSpecificationImpl::query).isEqualTo("This is my other query")
            prop(SqlPollStepSpecificationImpl::parameters).hasSize(0)
            prop(SqlPollStepSpecificationImpl::pollDelay).isEqualTo(Duration.ofSeconds(23))
            prop(SqlPollStepSpecificationImpl::monitoringConfig).all {
                prop(StepMonitoringConfiguration::meters).isTrue()
                prop(StepMonitoringConfiguration::events).isFalse()
            }
            prop(SqlPollStepSpecificationImpl::singletonConfiguration).all {
                prop(SingletonConfiguration::type).isEqualTo(SingletonType.BROADCAST)
                prop(SingletonConfiguration::bufferSize).isEqualTo(123)
                prop(SingletonConfiguration::idleTimeout).isEqualTo(Duration.ofSeconds(20))
            }
        }
    }
}
