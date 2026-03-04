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

package io.qalipsis.plugins.sql.search

import assertk.all
import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isNull
import assertk.assertions.isSameAs
import assertk.assertions.isTrue
import assertk.assertions.prop
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.scenario.StepSpecificationRegistry
import io.qalipsis.api.scenario.TestScenarioFactory
import io.qalipsis.api.steps.DummyStepSpecification
import io.qalipsis.api.steps.StepMonitoringConfiguration
import io.qalipsis.plugins.sql.SqlConnection
import io.qalipsis.plugins.sql.configuration.defaults
import io.qalipsis.plugins.sql.dialect.Protocol
import io.qalipsis.plugins.sql.sql
import io.qalipsis.test.mockk.relaxedMockk
import org.junit.jupiter.api.Test

/**
 * @author Fiodar Hmyza
 */
internal class SqlSearchStepSpecificationTest {

    @Test
    internal fun `should add minimal specification to the step`() {
        val previousStep = DummyStepSpecification()
        val queryFactory: suspend (ctx: StepContext<*, *>, input: Int) -> String = relaxedMockk()
        previousStep.sql().search {
            protocol(Protocol.POSTGRESQL)
            query(queryFactory)
            connection {
                host = "my-server"
                port = 5678
                database = "my-other-database"
                username = "my-other-username"
                password = "my-other-password"
            }
        }

        assertThat(previousStep.nextSteps[0]).isInstanceOf(SqlSearchStepSpecificationImpl::class).all {
            prop(SqlSearchStepSpecificationImpl<*>::name).isEmpty()
            prop(SqlSearchStepSpecificationImpl<*>::protocol).isEqualTo(Protocol.POSTGRESQL)
            prop(SqlSearchStepSpecificationImpl<*>::queryFactory).isSameAs(queryFactory)
            prop(SqlSearchStepSpecificationImpl<*>::parametersFactory).isNull()
            prop(SqlSearchStepSpecificationImpl<*>::connection).all {
                prop(SqlConnection::host).isEqualTo("my-server")
                prop(SqlConnection::port).isEqualTo(5678)
                prop(SqlConnection::database).isEqualTo("my-other-database")
                prop(SqlConnection::username).isEqualTo("my-other-username")
                prop(SqlConnection::password).isEqualTo("my-other-password")
            }
            prop(SqlSearchStepSpecificationImpl<*>::monitoringConfig).all {
                prop(StepMonitoringConfiguration::events).isFalse()
                prop(StepMonitoringConfiguration::meters).isFalse()
            }
        }
    }


    @Test
    internal fun `should add a complete specification to the step with monitoring`() {
        val previousStep = DummyStepSpecification()
        val queryFactory: suspend (ctx: StepContext<*, *>, input: Int) -> String = relaxedMockk()
        val paramsFactory: suspend (ctx: StepContext<*, *>, input: Int) -> List<*> = relaxedMockk()
        previousStep.sql().search {
            name = "my-other-step"
            protocol(Protocol.MYSQL)
            connection {
                host = "my-server"
                port = 5678
                database = "my-other-database"
                username = "my-other-username"
                password = "my-other-password"
            }
            query(queryFactory)
            parameters(paramsFactory)
            monitoring {
                meters = true
                events = false
            }
        }

        assertThat(previousStep.nextSteps[0]).isInstanceOf(SqlSearchStepSpecificationImpl::class).all {
            prop(SqlSearchStepSpecificationImpl<*>::name).isEqualTo("my-other-step")
            prop(SqlSearchStepSpecificationImpl<*>::protocol).isEqualTo(Protocol.MYSQL)
            prop(SqlSearchStepSpecificationImpl<*>::queryFactory).isSameAs(queryFactory)
            prop(SqlSearchStepSpecificationImpl<*>::parametersFactory).isSameAs(paramsFactory)
            prop(SqlSearchStepSpecificationImpl<*>::connection).all {
                prop(SqlConnection::host).isEqualTo("my-server")
                prop(SqlConnection::port).isEqualTo(5678)
                prop(SqlConnection::database).isEqualTo("my-other-database")
                prop(SqlConnection::username).isEqualTo("my-other-username")
                prop(SqlConnection::password).isEqualTo("my-other-password")
            }
            prop(SqlSearchStepSpecificationImpl<*>::monitoringConfig).all {
                prop(StepMonitoringConfiguration::events).isFalse()
                prop(StepMonitoringConfiguration::meters).isTrue()
            }
        }
    }

    @Test
    internal fun `should add a complete specification to the step with logger`() {
        val previousStep = DummyStepSpecification()
        val queryFactory: suspend (ctx: StepContext<*, *>, input: Int) -> String = relaxedMockk()
        val paramsFactory: suspend (ctx: StepContext<*, *>, input: Int) -> List<*> = relaxedMockk()
        previousStep.sql().search {
            name = "my-other-step"
            protocol(Protocol.MYSQL)
            connection {
                host = "my-server"
                port = 5678
                database = "my-other-database"
                username = "my-other-username"
                password = "my-other-password"
            }
            query(queryFactory)
            parameters(paramsFactory)
            monitoring {
                meters = false
                events = true
            }
        }

        assertThat(previousStep.nextSteps[0]).isInstanceOf(SqlSearchStepSpecificationImpl::class).all {
            prop(SqlSearchStepSpecificationImpl<*>::name).isEqualTo("my-other-step")
            prop(SqlSearchStepSpecificationImpl<*>::protocol).isEqualTo(Protocol.MYSQL)
            prop(SqlSearchStepSpecificationImpl<*>::queryFactory).isSameAs(queryFactory)
            prop(SqlSearchStepSpecificationImpl<*>::parametersFactory).isSameAs(paramsFactory)
            prop(SqlSearchStepSpecificationImpl<*>::connection).all {
                prop(SqlConnection::host).isEqualTo("my-server")
                prop(SqlConnection::port).isEqualTo(5678)
                prop(SqlConnection::database).isEqualTo("my-other-database")
                prop(SqlConnection::username).isEqualTo("my-other-username")
                prop(SqlConnection::password).isEqualTo("my-other-password")
            }
            prop(SqlSearchStepSpecificationImpl<*>::monitoringConfig).all {
                prop(StepMonitoringConfiguration::events).isTrue()
                prop(StepMonitoringConfiguration::meters).isFalse()
            }
        }
    }

    @Test
    internal fun `should apply defaults from SqlDefaultsExtension`() {
        val scenario = TestScenarioFactory.scenario("my-scenario", {
            sql().defaults {
                connection {
                    host = "default-host"
                    port = 5432
                    database = "default-db"
                    username = "default-user"
                }
                protocol(Protocol.POSTGRESQL)
                monitoring {
                    events = true
                    meters = true
                }
            }
        }) as StepSpecificationRegistry

        val previousStep = DummyStepSpecification()
        previousStep.scenario = scenario
        val queryFactory: suspend (ctx: StepContext<*, *>, input: Int) -> String = relaxedMockk()
        previousStep.sql().search {
            query(queryFactory)
        }

        assertThat(previousStep.nextSteps[0]).isInstanceOf(SqlSearchStepSpecificationImpl::class).all {
            prop(SqlSearchStepSpecificationImpl<*>::connection).all {
                prop(SqlConnection::host).isEqualTo("default-host")
                prop(SqlConnection::port).isEqualTo(5432)
                prop(SqlConnection::database).isEqualTo("default-db")
                prop(SqlConnection::username).isEqualTo("default-user")
            }
            prop(SqlSearchStepSpecificationImpl<*>::protocol).isEqualTo(Protocol.POSTGRESQL)
            prop(SqlSearchStepSpecificationImpl<*>::monitoringConfig).all {
                prop(StepMonitoringConfiguration::events).isTrue()
                prop(StepMonitoringConfiguration::meters).isTrue()
            }
        }
    }

    @Test
    internal fun `should allow overriding defaults from SqlDefaultsExtension`() {
        val scenario = TestScenarioFactory.scenario("my-scenario", {
            sql().defaults {
                connection {
                    host = "default-host"
                    port = 5432
                    database = "default-db"
                    username = "default-user"
                }
                protocol(Protocol.POSTGRESQL)
                monitoring {
                    events = true
                    meters = true
                }
            }
        }) as StepSpecificationRegistry

        val previousStep = DummyStepSpecification()
        previousStep.scenario = scenario
        val queryFactory: suspend (ctx: StepContext<*, *>, input: Int) -> String = relaxedMockk()
        previousStep.sql().search {
            connection {
                host = "override-host"
            }
            protocol(Protocol.MYSQL)
            monitoring {
                events = false
            }
            query(queryFactory)
        }

        assertThat(previousStep.nextSteps[0]).isInstanceOf(SqlSearchStepSpecificationImpl::class).all {
            prop(SqlSearchStepSpecificationImpl<*>::connection).all {
                prop(SqlConnection::host).isEqualTo("override-host")
                prop(SqlConnection::port).isEqualTo(5432)
                prop(SqlConnection::database).isEqualTo("default-db")
                prop(SqlConnection::username).isEqualTo("default-user")
            }
            prop(SqlSearchStepSpecificationImpl<*>::protocol).isEqualTo(Protocol.MYSQL)
            prop(SqlSearchStepSpecificationImpl<*>::monitoringConfig).all {
                prop(StepMonitoringConfiguration::events).isFalse()
                prop(StepMonitoringConfiguration::meters).isTrue()
            }
        }
    }

    @Test
    internal fun `should allow successive overwriting of defaults`() {
        val scenario = TestScenarioFactory.scenario("my-scenario", {
            sql().defaults {
                connection {
                    host = "default-host"
                    port = 5432
                    database = "default-db"
                    username = "default-user"
                }
                protocol(Protocol.POSTGRESQL)
                monitoring {
                    events = true
                    meters = true
                }
            }
        }) as StepSpecificationRegistry

        val previousStep = DummyStepSpecification()
        previousStep.scenario = scenario
        val queryFactory: suspend (ctx: StepContext<*, *>, input: Int) -> String = relaxedMockk()
        previousStep
            .sql().defaults {
                connection {
                    host = "step-default-host"
                    database = "step-db"
                }
            }
            .sql().search {
                connection {
                    host = "override-host"
                }
                monitoring {
                    events = false
                }
                query(queryFactory)
            }

        assertThat(previousStep.nextSteps[0]).isInstanceOf(SqlSearchStepSpecificationImpl::class).all {
            prop(SqlSearchStepSpecificationImpl<*>::connection).all {
                prop(SqlConnection::host).isEqualTo("override-host")
                prop(SqlConnection::port).isEqualTo(5432)
                prop(SqlConnection::database).isEqualTo("step-db")
                prop(SqlConnection::username).isEqualTo("default-user")
            }
            prop(SqlSearchStepSpecificationImpl<*>::protocol).isEqualTo(Protocol.POSTGRESQL)
            prop(SqlSearchStepSpecificationImpl<*>::monitoringConfig).all {
                prop(StepMonitoringConfiguration::events).isFalse()
                prop(StepMonitoringConfiguration::meters).isTrue()
            }
        }
    }

}
