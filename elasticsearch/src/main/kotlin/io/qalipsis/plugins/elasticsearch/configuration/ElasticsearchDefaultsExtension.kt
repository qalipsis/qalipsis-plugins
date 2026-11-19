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

import io.qalipsis.api.annotations.Spec
import io.qalipsis.api.scenario.ConfigurableScenarioSpecification
import io.qalipsis.api.scenario.ExtensibleScenarioSpecification
import io.qalipsis.api.scenario.StepSpecificationRegistry
import io.qalipsis.api.steps.StepMonitoringConfiguration
import io.qalipsis.api.steps.StepSpecification
import io.qalipsis.plugins.elasticsearch.AbstractElasticsearchQueryStepSpecification
import io.qalipsis.plugins.elasticsearch.ElasticsearchConfigurableScenarioSpecification
import io.qalipsis.plugins.elasticsearch.ElasticsearchScenarioSpecification
import io.qalipsis.plugins.elasticsearch.ElasticsearchStepSpecification
import io.qalipsis.plugins.elasticsearch.poll.ElasticsearchPollStepSpecificationImpl
import io.qalipsis.plugins.elasticsearch.save.ElasticsearchSaveStepSpecificationImpl
import org.elasticsearch.client.RestClient

internal const val EXTENSION_NAME = "elasticsearch.defaults"

/**
 * Public DSL interface for configuring Elasticsearch default client and monitoring.
 *
 * @author Eric Jessé
 */
interface ElasticsearchDefaultsExtension {

    /**
     * Configures the default REST client for all sibling Elasticsearch steps.
     */
    fun client(client: () -> RestClient)

    /**
     * Configures the default monitoring for all sibling Elasticsearch steps.
     */
    fun monitoring(monitoringConfig: StepMonitoringConfiguration.() -> Unit)
}

/**
 * Extension for the default client and monitoring configuration of all Elasticsearch steps in a scenario.
 *
 * When registered before the actual steps, the defaults are applied as pre-populated values.
 * Each step can still override any default value with its own explicit configuration.
 *
 * @author Eric Jessé
 */
@Spec
internal class ElasticsearchDefaultsExtensionImpl : ElasticsearchDefaultsExtension {

    internal var clientFactory: (() -> RestClient)? = null

    internal var monitoringConfig = StepMonitoringConfiguration().all()

    override fun client(client: () -> RestClient) {
        this.clientFactory = client
    }

    override fun monitoring(monitoringConfig: StepMonitoringConfiguration.() -> Unit) {
        this.monitoringConfig.monitoringConfig()
    }

    /**
     * Applies the default configuration to a poll step specification.
     */
    internal fun applyTo(spec: ElasticsearchPollStepSpecificationImpl) {
        clientFactory?.let { spec.client = it }
        spec.monitoringConfig = monitoringConfig.copy()
    }

    /**
     * Applies the default configuration to a save step specification.
     */
    internal fun applyTo(spec: ElasticsearchSaveStepSpecificationImpl<*>) {
        clientFactory?.let { spec.client = it }
        spec.monitoringConfig = monitoringConfig.copy()
    }

    /**
     * Applies the default configuration to a search or mget step specification.
     */
    internal fun applyTo(spec: AbstractElasticsearchQueryStepSpecification<*>) {
        clientFactory?.let { spec.client = it }
        spec.monitoringConfig = monitoringConfig.copy()
    }
}

/**
 * Registers default client and monitoring configuration for all Elasticsearch steps in this scenario.
 *
 * Must be called before the step specifications that should use the defaults.
 *
 * @author Eric Jessé
 */
fun ElasticsearchConfigurableScenarioSpecification.defaults(
    configurationBlock: ElasticsearchDefaultsExtension.() -> Unit,
): ConfigurableScenarioSpecification {
    val spec = ElasticsearchDefaultsExtensionImpl()
    spec.configurationBlock()
    (this.scenario as ExtensibleScenarioSpecification).addExtension(EXTENSION_NAME, spec)
    return this.scenario
}

/**
 * Registers default client and monitoring configuration for all Elasticsearch steps in this scenario.
 * When called on a step, the configuration is merged on top of any existing scenario-level defaults.
 *
 * Must be called before the step specifications that should use the defaults.
 *
 * @author Eric Jessé
 */
fun <INPUT, OUTPUT> ElasticsearchStepSpecification<INPUT, OUTPUT, *>.defaults(
    configurationBlock: ElasticsearchDefaultsExtension.() -> Unit,
): ElasticsearchStepSpecification<INPUT, OUTPUT, *> {
    val spec = findElasticsearchDefaults(this as StepSpecification<*, *, *>) ?: ElasticsearchDefaultsExtensionImpl()
    spec.configurationBlock()
    ((this as StepSpecification<*, *, *>).scenario as ExtensibleScenarioSpecification)
        .addExtension(EXTENSION_NAME, spec)
    return this
}

/**
 * Finds the [ElasticsearchDefaultsExtensionImpl] registered in the scenario, if any.
 */
internal fun findElasticsearchDefaults(registry: StepSpecificationRegistry): ElasticsearchDefaultsExtensionImpl? {
    return ((registry as ElasticsearchScenarioSpecification).scenario as ExtensibleScenarioSpecification)
        .getExtension(EXTENSION_NAME)
}

/**
 * Finds the [ElasticsearchDefaultsExtensionImpl] from a step specification's scenario.
 */
internal fun findElasticsearchDefaults(stepSpec: StepSpecification<*, *, *>): ElasticsearchDefaultsExtensionImpl? {
    return (stepSpec.scenario as ExtensibleScenarioSpecification).getExtension(EXTENSION_NAME)
}
