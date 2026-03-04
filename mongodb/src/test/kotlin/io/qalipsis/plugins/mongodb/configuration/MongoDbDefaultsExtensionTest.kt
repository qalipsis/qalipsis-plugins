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

package io.qalipsis.plugins.mongodb.configuration

import assertk.all
import assertk.assertThat
import assertk.assertions.isSameAs
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
            prop(MongoDbPollStepSpecificationImpl::client).isSameAs(clientFactory)
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
            prop(MongoDbSaveStepSpecificationImpl<*>::clientBuilder).isSameAs(clientFactory)
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
            prop(MongoDbSearchStepSpecificationImpl<*>::clientFactory).isSameAs(clientFactory)
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
            prop(MongoDbPollStepSpecificationImpl::client).isSameAs(originalClient)
            prop(MongoDbPollStepSpecificationImpl::monitoringConfig).all {
                prop(StepMonitoringConfiguration::events).isTrue()
            }
        }
    }
}
