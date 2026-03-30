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

package io.qalipsis.plugins.mongodb.save

import assertk.all
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.isSameInstanceAs
import assertk.assertions.isTrue
import assertk.assertions.prop
import com.mongodb.reactivestreams.client.MongoClients
import io.aerisconsulting.catadioptre.getProperty
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.scenario.StepSpecificationRegistry
import io.qalipsis.api.scenario.TestScenarioFactory
import io.qalipsis.api.steps.DummyStepSpecification
import io.qalipsis.api.steps.StepMonitoringConfiguration
import io.qalipsis.plugins.mongodb.configuration.defaults
import io.qalipsis.plugins.mongodb.mongodb
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.relaxedMockk
import org.bson.Document
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension


/**
 *
 * @author Alexander Sosnovsky
 */
internal class MongoDbSaveStepSpecificationImplTest {

    @JvmField
    @RegisterExtension
    val testDispatcherProvider = TestDispatcherProvider()

    private val databaseName: (suspend (ctx: StepContext<*, *>, input: Any?) -> String) = { _, _ -> "db" }

    private val collectionName: (suspend (ctx: StepContext<*, *>, input: Any) -> String) = { _, _ -> "col" }

    private val recordSupplier: (suspend (ctx: StepContext<*, *>, input: Any?) -> List<Document>) = { _, _ ->
        listOf(
            Document("key1", "val1"),
            Document("key3", "val3"),
            Document("key3-1", "val3-1")
        )
    }

    @Test
    fun `should add minimal configuration for the step`() = testDispatcherProvider.runTest {
        val previousStep = DummyStepSpecification()
        previousStep.mongodb().save {
            name = "my-save-step"
            connect {
                MongoClients.create()
            }
            query {
                database = databaseName
                collection = collectionName
                documents = recordSupplier
            }

        }

        assertThat(previousStep.nextSteps[0]).isInstanceOf(MongoDbSaveStepSpecificationImpl::class).all {
            prop("name") { MongoDbSaveStepSpecificationImpl<*>::name.call(it) }.isEqualTo("my-save-step")
            prop(MongoDbSaveStepSpecificationImpl<*>::clientBuilder).isNotNull()
            prop(MongoDbSaveStepSpecificationImpl<*>::queryConfig).all {
                prop(MongoDbSaveQueryConfiguration<*>::documents).isEqualTo(recordSupplier)
            }
            prop(MongoDbSaveStepSpecificationImpl<*>::monitoringConfig).isNotNull().all {
                prop(StepMonitoringConfiguration::events).isTrue()
                prop(StepMonitoringConfiguration::meters).isTrue()
            }
        }

        val step: MongoDbSaveStepSpecificationImpl<*> =
            previousStep.nextSteps[0] as MongoDbSaveStepSpecificationImpl<*>

        val database = step.queryConfig.getProperty<suspend (ctx: StepContext<*, *>, input: Int) -> String>("database")
        assertThat(database(relaxedMockk(), relaxedMockk())).isEqualTo("db")

        val collection =
            step.queryConfig.getProperty<suspend (ctx: StepContext<*, *>, input: Int) -> String>("collection")
        assertThat(collection(relaxedMockk(), relaxedMockk())).isEqualTo("col")
    }


    @Test
    fun `should add a complete configuration for the step`() = testDispatcherProvider.runTest {
        val previousStep = DummyStepSpecification()
        previousStep.mongodb().save {
            name = "my-save-step"
            connect {
                MongoClients.create()
            }
            query {
                database = databaseName
                collection = collectionName
                documents = recordSupplier
            }
            monitoring {
                events = true
                meters = true
            }
        }

        assertThat(previousStep.nextSteps[0]).isInstanceOf(MongoDbSaveStepSpecificationImpl::class).all {
            prop("name") { MongoDbSaveStepSpecificationImpl<*>::name.call(it) }.isEqualTo("my-save-step")
            prop(MongoDbSaveStepSpecificationImpl<*>::clientBuilder).isNotNull()
            prop(MongoDbSaveStepSpecificationImpl<*>::queryConfig).all {
                prop(MongoDbSaveQueryConfiguration<*>::documents).isEqualTo(recordSupplier)
            }
            prop(MongoDbSaveStepSpecificationImpl<*>::monitoringConfig).all {
                prop(StepMonitoringConfiguration::events).isTrue()
                prop(StepMonitoringConfiguration::meters).isTrue()
            }
        }

        val step: MongoDbSaveStepSpecificationImpl<*> =
            previousStep.nextSteps[0] as MongoDbSaveStepSpecificationImpl<*>

        val database = step.queryConfig.getProperty<suspend (ctx: StepContext<*, *>, input: Int) -> String>("database")
        assertThat(database(relaxedMockk(), relaxedMockk())).isEqualTo("db")

        val collection =
            step.queryConfig.getProperty<suspend (ctx: StepContext<*, *>, input: Int) -> String>("collection")
        assertThat(collection(relaxedMockk(), relaxedMockk())).isEqualTo("col")
    }

    @Test
    fun `should apply defaults from MongoDbDefaultsExtension`() = testDispatcherProvider.runTest {
        val clientFactory: () -> com.mongodb.reactivestreams.client.MongoClient = relaxedMockk()
        val scenario = TestScenarioFactory.scenario("my-scenario", {
            mongodb().defaults {
                connect(clientFactory)
                monitoring {
                    events = true
                    meters = true
                }
            }
        }) as StepSpecificationRegistry

        val previousStep = DummyStepSpecification()
        previousStep.scenario = scenario
        previousStep.mongodb().save {
            name = "my-save-step"
            query {
                database = databaseName
                collection = collectionName
                documents = recordSupplier
            }
        }

        assertThat(previousStep.nextSteps[0]).isInstanceOf(MongoDbSaveStepSpecificationImpl::class).all {
            prop(MongoDbSaveStepSpecificationImpl<*>::clientBuilder).isSameInstanceAs(clientFactory)
            prop(MongoDbSaveStepSpecificationImpl<*>::monitoringConfig).all {
                prop(StepMonitoringConfiguration::events).isTrue()
                prop(StepMonitoringConfiguration::meters).isTrue()
            }
        }
    }

    @Test
    fun `should allow overriding defaults from MongoDbDefaultsExtension`() = testDispatcherProvider.runTest {
        val defaultClient: () -> com.mongodb.reactivestreams.client.MongoClient = relaxedMockk()
        val overrideClient: () -> com.mongodb.reactivestreams.client.MongoClient = relaxedMockk()
        val scenario = TestScenarioFactory.scenario("my-scenario", {
            mongodb().defaults {
                connect(defaultClient)
                monitoring {
                    events = true
                    meters = true
                }
            }
        }) as StepSpecificationRegistry

        val previousStep = DummyStepSpecification()
        previousStep.scenario = scenario
        previousStep.mongodb().save {
            name = "my-save-step"
            connect(overrideClient)
            monitoring {
                events = false
            }
            query {
                database = databaseName
                collection = collectionName
                documents = recordSupplier
            }
        }

        assertThat(previousStep.nextSteps[0]).isInstanceOf(MongoDbSaveStepSpecificationImpl::class).all {
            prop(MongoDbSaveStepSpecificationImpl<*>::clientBuilder).isSameInstanceAs(overrideClient)
            prop(MongoDbSaveStepSpecificationImpl<*>::monitoringConfig).all {
                prop(StepMonitoringConfiguration::events).isFalse()
                prop(StepMonitoringConfiguration::meters).isTrue()
            }
        }
    }

    @Test
    fun `should allow successive overwriting of defaults`() = testDispatcherProvider.runTest {
        val defaultClient: () -> com.mongodb.reactivestreams.client.MongoClient = relaxedMockk()
        val stepDefaultClient: () -> com.mongodb.reactivestreams.client.MongoClient = relaxedMockk()
        val overrideClient: () -> com.mongodb.reactivestreams.client.MongoClient = relaxedMockk()
        val scenario = TestScenarioFactory.scenario("my-scenario", {
            mongodb().defaults {
                connect(defaultClient)
                monitoring {
                    events = true
                    meters = true
                }
            }
        }) as StepSpecificationRegistry

        val previousStep = DummyStepSpecification()
        previousStep.scenario = scenario
        previousStep
            .mongodb().defaults {
                connect(stepDefaultClient)
            }
            .mongodb().save {
                connect(overrideClient)
                monitoring {
                    events = false
                }
                query {
                    database = databaseName
                    collection = collectionName
                    documents = recordSupplier
                }
            }

        assertThat(previousStep.nextSteps[0]).isInstanceOf(MongoDbSaveStepSpecificationImpl::class).all {
            prop(MongoDbSaveStepSpecificationImpl<*>::clientBuilder).isSameInstanceAs(overrideClient)
            prop(MongoDbSaveStepSpecificationImpl<*>::monitoringConfig).all {
                prop(StepMonitoringConfiguration::events).isFalse()
                prop(StepMonitoringConfiguration::meters).isTrue()
            }
        }
    }
}
