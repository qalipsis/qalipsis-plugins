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

import io.qalipsis.api.annotations.Spec
import io.qalipsis.api.scenario.ConfigurableScenarioSpecification
import io.qalipsis.api.scenario.ExtensibleScenarioSpecification
import io.qalipsis.api.scenario.StepSpecificationRegistry
import io.qalipsis.api.steps.StepMonitoringConfiguration
import io.qalipsis.api.steps.StepSpecification
import io.qalipsis.plugins.mongodb.MongoDbConfigurableScenarioSpecification
import io.qalipsis.plugins.mongodb.MongoDbScenarioSpecification
import io.qalipsis.plugins.mongodb.MongoDbStepSpecification
import io.qalipsis.plugins.mongodb.poll.MongoDbPollStepSpecificationImpl
import io.qalipsis.plugins.mongodb.save.MongoDbSaveStepSpecificationImpl
import io.qalipsis.plugins.mongodb.search.MongoDbSearchStepSpecificationImpl

internal const val EXTENSION_NAME = "mongodb.defaults"

/**
 * Public DSL interface for configuring MongoDB default connection and monitoring.
 *
 * @author Eric Jessé
 */
interface MongoDbDefaultsExtension {

    /**
     * Configures the default client factory for all sibling MongoDB steps.
     */
    fun connect(client: () -> com.mongodb.reactivestreams.client.MongoClient)

    /**
     * Configures the default monitoring for all sibling MongoDB steps.
     */
    fun monitoring(monitoringConfig: StepMonitoringConfiguration.() -> Unit)
}

/**
 * Extension for the default connection and monitoring configuration of all MongoDB steps in a scenario.
 *
 * When registered before the actual steps, the defaults are applied as pre-populated values.
 * Each step can still override any default value with its own explicit configuration.
 *
 * @author Eric Jessé
 */
@Spec
internal class MongoDbDefaultsExtensionImpl : MongoDbDefaultsExtension {

    internal var clientFactory: (() -> com.mongodb.reactivestreams.client.MongoClient)? = null

    internal var monitoringConfig = StepMonitoringConfiguration()

    override fun connect(client: () -> com.mongodb.reactivestreams.client.MongoClient) {
        this.clientFactory = client
    }

    override fun monitoring(monitoringConfig: StepMonitoringConfiguration.() -> Unit) {
        this.monitoringConfig.monitoringConfig()
    }

    /**
     * Applies the default configuration to a poll step specification.
     */
    internal fun applyTo(spec: MongoDbPollStepSpecificationImpl) {
        clientFactory?.let { spec.client = it }
        spec.monitoringConfig = monitoringConfig.copy()
    }

    /**
     * Applies the default configuration to a save step specification.
     */
    internal fun applyTo(spec: MongoDbSaveStepSpecificationImpl<*>) {
        clientFactory?.let { spec.clientBuilder = it }
        spec.monitoringConfig = monitoringConfig.copy()
    }

    /**
     * Applies the default configuration to a search step specification.
     */
    internal fun applyTo(spec: MongoDbSearchStepSpecificationImpl<*>) {
        clientFactory?.let { spec.clientFactory = it }
        spec.monitoringConfig = monitoringConfig.copy()
    }
}

/**
 * Registers default connection and monitoring configuration for all MongoDB steps in this scenario.
 *
 * Must be called before the step specifications that should use the defaults.
 *
 * @author Eric Jessé
 */
fun MongoDbConfigurableScenarioSpecification.defaults(
    configurationBlock: MongoDbDefaultsExtension.() -> Unit,
): ConfigurableScenarioSpecification {
    val spec = MongoDbDefaultsExtensionImpl()
    spec.configurationBlock()
    (this.scenario as ExtensibleScenarioSpecification).addExtension(EXTENSION_NAME, spec)
    return this.scenario
}

/**
 * Registers default connection and monitoring configuration for all MongoDB steps in this scenario.
 * When called on a step, the configuration is merged on top of any existing scenario-level defaults.
 *
 * Must be called before the step specifications that should use the defaults.
 *
 * @author Eric Jessé
 */
fun <INPUT, OUTPUT> MongoDbStepSpecification<INPUT, OUTPUT, *>.defaults(
    configurationBlock: MongoDbDefaultsExtension.() -> Unit,
): MongoDbStepSpecification<INPUT, OUTPUT, *> {
    val spec = findMongoDbDefaults(this as StepSpecification<*, *, *>) ?: MongoDbDefaultsExtensionImpl()
    spec.configurationBlock()
    ((this as StepSpecification<*, *, *>).scenario as ExtensibleScenarioSpecification)
        .addExtension(EXTENSION_NAME, spec)
    return this
}

/**
 * Finds the [MongoDbDefaultsExtensionImpl] registered in the scenario, if any.
 */
internal fun findMongoDbDefaults(registry: StepSpecificationRegistry): MongoDbDefaultsExtensionImpl? {
    return ((registry as MongoDbScenarioSpecification).scenario as ExtensibleScenarioSpecification)
        .getExtension(EXTENSION_NAME)
}

/**
 * Finds the [MongoDbDefaultsExtensionImpl] from a step specification's scenario.
 */
internal fun findMongoDbDefaults(stepSpec: StepSpecification<*, *, *>): MongoDbDefaultsExtensionImpl? {
    return (stepSpec.scenario as ExtensibleScenarioSpecification).getExtension(EXTENSION_NAME)
}
