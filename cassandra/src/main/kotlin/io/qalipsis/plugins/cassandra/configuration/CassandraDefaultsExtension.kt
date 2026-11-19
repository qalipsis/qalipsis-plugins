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

package io.qalipsis.plugins.cassandra.configuration

import io.qalipsis.api.annotations.Spec
import io.qalipsis.api.scenario.ConfigurableScenarioSpecification
import io.qalipsis.api.scenario.ExtensibleScenarioSpecification
import io.qalipsis.api.scenario.StepSpecificationRegistry
import io.qalipsis.api.steps.StepMonitoringConfiguration
import io.qalipsis.api.steps.StepSpecification
import io.qalipsis.plugins.cassandra.CassandraConfigurableScenarioSpecification
import io.qalipsis.plugins.cassandra.CassandraNamespaceScenarioSpecification
import io.qalipsis.plugins.cassandra.CassandraStepSpecification
import io.qalipsis.plugins.cassandra.poll.CassandraPollStepSpecificationImpl
import io.qalipsis.plugins.cassandra.save.CassandraSaveStepSpecificationImpl
import io.qalipsis.plugins.cassandra.search.CassandraSearchStepSpecificationImpl

internal const val EXTENSION_NAME = "cassandra.defaults"

/**
 * Public DSL interface for configuring Cassandra default connection and monitoring.
 *
 * @author Eric Jessé
 */
interface CassandraDefaultsExtension {

    /**
     * Configures the default connection of the Cassandra cluster for all sibling steps.
     */
    fun connect(configurationBlock: CassandraServerConfiguration.() -> Unit)

    /**
     * Configures the default monitoring for all sibling steps.
     */
    fun monitoring(monitoringConfig: StepMonitoringConfiguration.() -> Unit)
}

/**
 * Extension for the default connection and monitoring configuration of all Cassandra steps in a scenario.
 *
 * When registered before the actual steps, the defaults are applied as pre-populated values.
 * Each step can still override any default value with its own explicit configuration.
 *
 * @author Eric Jessé
 */
@Spec
internal class CassandraDefaultsExtensionImpl : CassandraDefaultsExtension {

    internal var serversConfig = CassandraServerConfiguration()

    internal var monitoringConfig = StepMonitoringConfiguration().all()

    override fun connect(configurationBlock: CassandraServerConfiguration.() -> Unit) {
        serversConfig.configurationBlock()
    }

    override fun monitoring(monitoringConfig: StepMonitoringConfiguration.() -> Unit) {
        this.monitoringConfig.monitoringConfig()
    }

    /**
     * Applies the default configuration to a poll step specification.
     * Note: poll's monitoringConfig is a val, so we copy the fields individually.
     */
    internal fun applyTo(spec: CassandraPollStepSpecificationImpl) {
        spec.serversConfig = serversConfig.copy()
        spec.monitoringConfig = monitoringConfig.copy()
    }

    /**
     * Applies the default configuration to a save step specification.
     */
    internal fun applyTo(spec: CassandraSaveStepSpecificationImpl<*>) {
        spec.serversConfig = serversConfig.copy()
        spec.monitoringConfig = monitoringConfig.copy()
    }

    /**
     * Applies the default configuration to a search step specification.
     */
    internal fun applyTo(spec: CassandraSearchStepSpecificationImpl<*>) {
        spec.serversConfig = serversConfig.copy()
        spec.monitoringConfig = monitoringConfig.copy()
    }
}

/**
 * Registers default connection and monitoring configuration for all Cassandra steps in this scenario.
 *
 * Must be called before the step specifications that should use the defaults.
 *
 * @author Eric Jessé
 */
fun CassandraConfigurableScenarioSpecification.defaults(
    configurationBlock: CassandraDefaultsExtension.() -> Unit,
): ConfigurableScenarioSpecification {
    val spec = CassandraDefaultsExtensionImpl()
    spec.configurationBlock()
    (this.scenario as ExtensibleScenarioSpecification).addExtension(EXTENSION_NAME, spec)
    return this.scenario
}

/**
 * Registers default connection and monitoring configuration for all Cassandra steps in this scenario.
 * When called on a step, the configuration is merged on top of any existing scenario-level defaults.
 *
 * Must be called before the step specifications that should use the defaults.
 *
 * @author Eric Jessé
 */
fun <INPUT, OUTPUT> CassandraStepSpecification<INPUT, OUTPUT, *>.defaults(
    configurationBlock: CassandraDefaultsExtension.() -> Unit,
): CassandraStepSpecification<INPUT, OUTPUT, *> {
    val spec = findCassandraDefaults(this) ?: CassandraDefaultsExtensionImpl()
    spec.configurationBlock()
    ((this as StepSpecification<*, *, *>).scenario as ExtensibleScenarioSpecification)
        .addExtension(EXTENSION_NAME, spec)
    return this
}

/**
 * Finds the [CassandraDefaultsExtensionImpl] registered in the scenario, if any.
 */
internal fun findCassandraDefaults(registry: StepSpecificationRegistry): CassandraDefaultsExtensionImpl? {
    return ((registry as CassandraNamespaceScenarioSpecification).scenario as ExtensibleScenarioSpecification)
        .getExtension(EXTENSION_NAME)
}

/**
 * Finds the [CassandraDefaultsExtensionImpl] from a step specification's scenario.
 */
internal fun findCassandraDefaults(stepSpec: StepSpecification<*, *, *>): CassandraDefaultsExtensionImpl? {
    return (stepSpec.scenario as ExtensibleScenarioSpecification).getExtension(EXTENSION_NAME)
}
