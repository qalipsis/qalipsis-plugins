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

package io.qalipsis.plugins.mongodb.configuration

import assertk.all
import assertk.assertThat
import assertk.assertions.isSameInstanceAs
import assertk.assertions.isTrue
import assertk.assertions.prop
import io.qalipsis.api.steps.StepMonitoringConfiguration
import io.qalipsis.plugins.mongodb.poll.MongoDbPollStepSpecificationImpl
import io.qalipsis.plugins.mongodb.save.MongoDbSaveStepSpecificationImpl
import io.qalipsis.plugins.mongodb.search.MongoDbSearchStepSpecificationImpl
import io.qalipsis.test.mockk.relaxedMockk
import org.junit.jupiter.api.Test

/**
 * @author Eric Jessé
 */
internal class MongoDbDefaultsExtensionTest {

    @Test
    fun `should apply defaults to poll step`() {
        val clientFactory: () -> com.mongodb.reactivestreams.client.MongoClient = relaxedMockk()
        val defaults = MongoDbDefaultsExtensionImpl()
        defaults.connect(clientFactory)
        defaults.monitoring {
            events = true
            meters = true
        }

        val spec = MongoDbPollStepSpecificationImpl()
        defaults.applyTo(spec)

        assertThat(spec).all {
            prop(MongoDbPollStepSpecificationImpl::client).isSameInstanceAs(clientFactory)
            prop(MongoDbPollStepSpecificationImpl::monitoringConfig).all {
                prop(StepMonitoringConfiguration::events).isTrue()
                prop(StepMonitoringConfiguration::meters).isTrue()
            }
        }
    }

    @Test
    fun `should apply defaults to save step`() {
        val clientFactory: () -> com.mongodb.reactivestreams.client.MongoClient = relaxedMockk()
        val defaults = MongoDbDefaultsExtensionImpl()
        defaults.connect(clientFactory)
        defaults.monitoring {
            events = true
            meters = true
        }

        val spec = MongoDbSaveStepSpecificationImpl<Any>()
        defaults.applyTo(spec)

        assertThat(spec).all {
            prop(MongoDbSaveStepSpecificationImpl<*>::clientBuilder).isSameInstanceAs(clientFactory)
            prop(MongoDbSaveStepSpecificationImpl<*>::monitoringConfig).all {
                prop(StepMonitoringConfiguration::events).isTrue()
                prop(StepMonitoringConfiguration::meters).isTrue()
            }
        }
    }

    @Test
    fun `should apply defaults to search step`() {
        val clientFactory: () -> com.mongodb.reactivestreams.client.MongoClient = relaxedMockk()
        val defaults = MongoDbDefaultsExtensionImpl()
        defaults.connect(clientFactory)
        defaults.monitoring {
            events = true
            meters = true
        }

        val spec = MongoDbSearchStepSpecificationImpl<Any>()
        defaults.applyTo(spec)

        assertThat(spec).all {
            prop(MongoDbSearchStepSpecificationImpl<*>::clientFactory).isSameInstanceAs(clientFactory)
            prop(MongoDbSearchStepSpecificationImpl<*>::monitoringConfig).all {
                prop(StepMonitoringConfiguration::events).isTrue()
                prop(StepMonitoringConfiguration::meters).isTrue()
            }
        }
    }

    @Test
    fun `should not apply client when not set in defaults`() {
        val defaults = MongoDbDefaultsExtensionImpl()
        defaults.monitoring {
            events = true
        }

        val spec = MongoDbPollStepSpecificationImpl()
        val originalClient = spec.client
        defaults.applyTo(spec)

        assertThat(spec).all {
            prop(MongoDbPollStepSpecificationImpl::client).isSameInstanceAs(originalClient)
            prop(MongoDbPollStepSpecificationImpl::monitoringConfig).all {
                prop(StepMonitoringConfiguration::events).isTrue()
            }
        }
    }
}
