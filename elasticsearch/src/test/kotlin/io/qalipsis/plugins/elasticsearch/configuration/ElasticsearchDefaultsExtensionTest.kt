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

package io.qalipsis.plugins.elasticsearch.configuration

import assertk.all
import assertk.assertThat
import assertk.assertions.isSameInstanceAs
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
            prop(ElasticsearchPollStepSpecificationImpl::client).isSameInstanceAs(clientFactory)
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
            prop(ElasticsearchSaveStepSpecificationImpl<*>::client).isSameInstanceAs(clientFactory)
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
            prop(AbstractElasticsearchQueryStepSpecification<*>::client).isSameInstanceAs(clientFactory)
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
            prop(AbstractElasticsearchQueryStepSpecification<*>::client).isSameInstanceAs(clientFactory)
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
            prop(ElasticsearchPollStepSpecificationImpl::client).isSameInstanceAs(originalClient)
            prop(ElasticsearchPollStepSpecificationImpl::monitoringConfig).all {
                prop(StepMonitoringConfiguration::events).isTrue()
            }
        }
    }
}
