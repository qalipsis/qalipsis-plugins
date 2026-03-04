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

package io.qalipsis.plugins.graphite.configuration

import io.qalipsis.api.annotations.Spec
import io.qalipsis.api.scenario.ConfigurableScenarioSpecification
import io.qalipsis.api.scenario.ExtensibleScenarioSpecification
import io.qalipsis.api.scenario.StepSpecificationRegistry
import io.qalipsis.api.steps.StepMonitoringConfiguration
import io.qalipsis.api.steps.StepSpecification
import io.qalipsis.plugins.graphite.GraphiteConfigurableScenarioSpecification
import io.qalipsis.plugins.graphite.GraphiteConnectionSpecification
import io.qalipsis.plugins.graphite.GraphiteScenarioSpecification
import io.qalipsis.plugins.graphite.GraphiteStepSpecification
import io.qalipsis.plugins.graphite.poll.GraphitePollStepSpecificationImpl
import io.qalipsis.plugins.graphite.save.GraphiteSaveStepSpecificationImpl
import io.qalipsis.plugins.graphite.search.GraphiteHttpConnectionSpecification

internal const val EXTENSION_NAME = "graphite.defaults"

/**
 * Public DSL interface for configuring Graphite default connections and monitoring.
 *
 * @author Eric Jesse
 */
interface GraphiteDefaultsExtension {

    /**
     * Configures the default connection for poll steps (HTTP connection for Graphite render API).
     */
    fun pollConnection(connection: GraphiteHttpConnectionSpecification.() -> Unit)

    /**
     * Configures the default connection for save steps (TCP/socket connection for Graphite plaintext protocol).
     */
    fun saveConnection(connection: GraphiteConnectionSpecification.() -> Unit)

    /**
     * Configures the default monitoring for all sibling Graphite steps.
     */
    fun monitoring(monitoringConfig: StepMonitoringConfiguration.() -> Unit)
}

/**
 * Extension for the default connections and monitoring configuration of all Graphite steps in a scenario.
 *
 * When registered before the actual steps, the defaults are applied as pre-populated values.
 * Each step can still override any default value with its own explicit configuration.
 *
 * @author Eric Jesse
 */
@Spec
internal class GraphiteDefaultsExtensionImpl : GraphiteDefaultsExtension {

    internal var pollConnectionConfig: (GraphiteHttpConnectionSpecification.() -> Unit)? = null

    internal var saveConnectionConfig: (GraphiteConnectionSpecification.() -> Unit)? = null

    internal var monitoringConfig = StepMonitoringConfiguration()

    override fun pollConnection(connection: GraphiteHttpConnectionSpecification.() -> Unit) {
        this.pollConnectionConfig = connection
    }

    override fun saveConnection(connection: GraphiteConnectionSpecification.() -> Unit) {
        this.saveConnectionConfig = connection
    }

    override fun monitoring(monitoringConfig: StepMonitoringConfiguration.() -> Unit) {
        this.monitoringConfig.monitoringConfig()
    }

    /**
     * Applies the default configuration to a poll step specification.
     */
    internal fun applyTo(spec: GraphitePollStepSpecificationImpl) {
        pollConnectionConfig?.let { spec.connectionConfiguration.it() }
        spec.monitoringConfiguration = monitoringConfig.copy()
    }

    /**
     * Applies the default configuration to a save step specification.
     */
    internal fun applyTo(spec: GraphiteSaveStepSpecificationImpl<*>) {
        saveConnectionConfig?.let { spec.connectionConfig.it() }
        spec.monitoringConfig = monitoringConfig.copy()
    }
}

/**
 * Registers default connections and monitoring configuration for all Graphite steps in this scenario.
 *
 * Must be called before the step specifications that should use the defaults.
 *
 * @author Eric Jesse
 */
fun GraphiteConfigurableScenarioSpecification.defaults(
    configurationBlock: GraphiteDefaultsExtension.() -> Unit,
): ConfigurableScenarioSpecification {
    val spec = GraphiteDefaultsExtensionImpl()
    spec.configurationBlock()
    (this.scenario as ExtensibleScenarioSpecification).addExtension(EXTENSION_NAME, spec)
    return this.scenario
}

/**
 * Registers default connections and monitoring configuration for all Graphite steps in this scenario.
 * When called on a step, the configuration is merged on top of any existing scenario-level defaults.
 *
 * Must be called before the step specifications that should use the defaults.
 *
 * @author Eric Jesse
 */
fun <INPUT, OUTPUT> GraphiteStepSpecification<INPUT, OUTPUT, *>.defaults(
    configurationBlock: GraphiteDefaultsExtension.() -> Unit,
): GraphiteStepSpecification<INPUT, OUTPUT, *> {
    val spec = findGraphiteDefaults(this as StepSpecification<*, *, *>) ?: GraphiteDefaultsExtensionImpl()
    spec.configurationBlock()
    ((this as StepSpecification<*, *, *>).scenario as ExtensibleScenarioSpecification)
        .addExtension(EXTENSION_NAME, spec)
    return this
}

/**
 * Finds the [GraphiteDefaultsExtensionImpl] registered in the scenario, if any.
 */
internal fun findGraphiteDefaults(registry: StepSpecificationRegistry): GraphiteDefaultsExtensionImpl? {
    return ((registry as GraphiteScenarioSpecification).scenario as ExtensibleScenarioSpecification)
        .getExtension(EXTENSION_NAME)
}

/**
 * Finds the [GraphiteDefaultsExtensionImpl] from a step specification's scenario.
 */
internal fun findGraphiteDefaults(stepSpec: StepSpecification<*, *, *>): GraphiteDefaultsExtensionImpl? {
    return (stepSpec.scenario as ExtensibleScenarioSpecification).getExtension(EXTENSION_NAME)
}
