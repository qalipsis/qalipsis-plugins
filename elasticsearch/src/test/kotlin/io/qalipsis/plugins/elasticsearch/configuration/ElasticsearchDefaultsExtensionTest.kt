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

package io.qalipsis.plugins.elasticsearch.configuration

import assertk.all
import assertk.assertThat
import assertk.assertions.isSameAs
import assertk.assertions.isTrue
import assertk.assertions.prop
import io.qalipsis.api.steps.StepMonitoringConfiguration
import io.qalipsis.plugins.elasticsearch.AbstractElasticsearchQueryStepSpecification
import io.qalipsis.plugins.elasticsearch.mget.ElasticsearchMultiGetStepSpecificationImpl
import io.qalipsis.plugins.elasticsearch.poll.ElasticsearchPollStepSpecificationImpl
import io.qalipsis.plugins.elasticsearch.save.ElasticsearchSaveStepSpecificationImpl
import io.qalipsis.plugins.elasticsearch.search.ElasticsearchSearchStepSpecificationImpl
import io.qalipsis.test.mockk.relaxedMockk
import org.elasticsearch.client.RestClient
import org.junit.jupiter.api.Test

/**
 * @author Eric Jesse
 */
internal class ElasticsearchDefaultsExtensionTest {

    @Test
    fun `should apply defaults to poll step`() {
        val clientFactory: () -> RestClient = relaxedMockk()
        val defaults = ElasticsearchDefaultsExtensionImpl()
        defaults.client(clientFactory)
        defaults.monitoring {
            events = true
            meters = true
        }

        val spec = ElasticsearchPollStepSpecificationImpl()
        defaults.applyTo(spec)

        assertThat(spec).all {
            prop(ElasticsearchPollStepSpecificationImpl::client).isSameAs(clientFactory)
            prop(ElasticsearchPollStepSpecificationImpl::monitoringConfig).all {
                prop(StepMonitoringConfiguration::events).isTrue()
                prop(StepMonitoringConfiguration::meters).isTrue()
            }
        }
    }

    @Test
    fun `should apply defaults to save step`() {
        val clientFactory: () -> RestClient = relaxedMockk()
        val defaults = ElasticsearchDefaultsExtensionImpl()
        defaults.client(clientFactory)
        defaults.monitoring {
            events = true
            meters = true
        }

        val spec = ElasticsearchSaveStepSpecificationImpl<Any>()
        defaults.applyTo(spec)

        assertThat(spec).all {
            prop(ElasticsearchSaveStepSpecificationImpl<*>::client).isSameAs(clientFactory)
            prop(ElasticsearchSaveStepSpecificationImpl<*>::monitoringConfig).all {
                prop(StepMonitoringConfiguration::events).isTrue()
                prop(StepMonitoringConfiguration::meters).isTrue()
            }
        }
    }

    @Test
    fun `should apply defaults to search step`() {
        val clientFactory: () -> RestClient = relaxedMockk()
        val defaults = ElasticsearchDefaultsExtensionImpl()
        defaults.client(clientFactory)
        defaults.monitoring {
            events = true
            meters = true
        }

        val spec = ElasticsearchSearchStepSpecificationImpl<Any>()
        defaults.applyTo(spec)

        assertThat(spec).all {
            prop(AbstractElasticsearchQueryStepSpecification<*>::client).isSameAs(clientFactory)
            prop(AbstractElasticsearchQueryStepSpecification<*>::monitoringConfig).all {
                prop(StepMonitoringConfiguration::events).isTrue()
                prop(StepMonitoringConfiguration::meters).isTrue()
            }
        }
    }

    @Test
    fun `should apply defaults to mget step`() {
        val clientFactory: () -> RestClient = relaxedMockk()
        val defaults = ElasticsearchDefaultsExtensionImpl()
        defaults.client(clientFactory)
        defaults.monitoring {
            events = true
            meters = true
        }

        val spec = ElasticsearchMultiGetStepSpecificationImpl<Any>()
        defaults.applyTo(spec)

        assertThat(spec).all {
            prop(AbstractElasticsearchQueryStepSpecification<*>::client).isSameAs(clientFactory)
            prop(AbstractElasticsearchQueryStepSpecification<*>::monitoringConfig).all {
                prop(StepMonitoringConfiguration::events).isTrue()
                prop(StepMonitoringConfiguration::meters).isTrue()
            }
        }
    }

    @Test
    fun `should not apply client when not set in defaults`() {
        val defaults = ElasticsearchDefaultsExtensionImpl()
        defaults.monitoring {
            events = true
        }

        val spec = ElasticsearchPollStepSpecificationImpl()
        val originalClient = spec.client
        defaults.applyTo(spec)

        assertThat(spec).all {
            prop(ElasticsearchPollStepSpecificationImpl::client).isSameAs(originalClient)
            prop(ElasticsearchPollStepSpecificationImpl::monitoringConfig).all {
                prop(StepMonitoringConfiguration::events).isTrue()
            }
        }
    }
}
