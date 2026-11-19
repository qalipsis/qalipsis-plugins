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

package io.qalipsis.plugins.sql.search

import assertk.all
import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isNull
import assertk.assertions.isSameInstanceAs
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
            prop(SqlSearchStepSpecificationImpl<*>::queryFactory).isSameInstanceAs(queryFactory)
            prop(SqlSearchStepSpecificationImpl<*>::parametersFactory).isNull()
            prop(SqlSearchStepSpecificationImpl<*>::connection).all {
                prop(SqlConnection::host).isEqualTo("my-server")
                prop(SqlConnection::port).isEqualTo(5678)
                prop(SqlConnection::database).isEqualTo("my-other-database")
                prop(SqlConnection::username).isEqualTo("my-other-username")
                prop(SqlConnection::password).isEqualTo("my-other-password")
            }
            prop(SqlSearchStepSpecificationImpl<*>::monitoringConfig).all {
                prop(StepMonitoringConfiguration::events).isTrue()
                prop(StepMonitoringConfiguration::meters).isTrue()
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
            prop(SqlSearchStepSpecificationImpl<*>::queryFactory).isSameInstanceAs(queryFactory)
            prop(SqlSearchStepSpecificationImpl<*>::parametersFactory).isSameInstanceAs(paramsFactory)
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
            prop(SqlSearchStepSpecificationImpl<*>::queryFactory).isSameInstanceAs(queryFactory)
            prop(SqlSearchStepSpecificationImpl<*>::parametersFactory).isSameInstanceAs(paramsFactory)
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
