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

package io.qalipsis.plugins.influxdb.configuration

import io.qalipsis.api.annotations.Spec
import io.qalipsis.api.scenario.ConfigurableScenarioSpecification
import io.qalipsis.api.scenario.ExtensibleScenarioSpecification
import io.qalipsis.api.scenario.StepSpecificationRegistry
import io.qalipsis.api.steps.StepMonitoringConfiguration
import io.qalipsis.api.steps.StepSpecification
import io.qalipsis.plugins.influxdb.InfluxDbStepConnection
import io.qalipsis.plugins.influxdb.InfluxDbStepConnectionImpl
import io.qalipsis.plugins.influxdb.InfluxdbConfigurableScenarioSpecification
import io.qalipsis.plugins.influxdb.InfluxdbScenarioSpecification
import io.qalipsis.plugins.influxdb.InfluxdbStepSpecification
import io.qalipsis.plugins.influxdb.poll.InfluxDbPollStepSpecificationImpl
import io.qalipsis.plugins.influxdb.save.InfluxDbSaveStepSpecificationImpl
import io.qalipsis.plugins.influxdb.search.InfluxDbSearchStepSpecificationImpl

internal const val EXTENSION_NAME = "influxdb.defaults"

/**
 * Public DSL interface for configuring InfluxDB default connection and monitoring.
 *
 * @author Eric Jessé
 */
interface InfluxDbDefaultsExtension {

    /**
     * Configures the default connection for all sibling InfluxDB steps.
     */
    fun connect(connection: InfluxDbStepConnection.() -> Unit)

    /**
     * Configures the default monitoring for all sibling InfluxDB steps.
     */
    fun monitoring(monitoringConfig: StepMonitoringConfiguration.() -> Unit)
}

/**
 * Extension for the default connection and monitoring configuration of all InfluxDB steps in a scenario.
 *
 * When registered before the actual steps, the defaults are applied as pre-populated values.
 * Each step can still override any default value with its own explicit configuration.
 *
 * @author Eric Jessé
 */
@Spec
internal class InfluxDbDefaultsExtensionImpl : InfluxDbDefaultsExtension {

    internal var connectionConfiguration = InfluxDbStepConnectionImpl()

    internal var monitoringConfig = StepMonitoringConfiguration().all()

    override fun connect(connection: InfluxDbStepConnection.() -> Unit) {
        connectionConfiguration.connection()
    }

    override fun monitoring(monitoringConfig: StepMonitoringConfiguration.() -> Unit) {
        this.monitoringConfig.monitoringConfig()
    }

    /**
     * Creates a copy of the connection configuration.
     */
    private fun copyConnection(): InfluxDbStepConnectionImpl {
        val copy = InfluxDbStepConnectionImpl()
        copy.url = connectionConfiguration.url
        copy.bucket = connectionConfiguration.bucket
        copy.user = connectionConfiguration.user
        copy.password = connectionConfiguration.password
        copy.org = connectionConfiguration.org
        copy.gzipEnabled = connectionConfiguration.gzipEnabled
        return copy
    }

    /**
     * Applies the default configuration to a poll step specification.
     */
    internal fun applyTo(spec: InfluxDbPollStepSpecificationImpl) {
        spec.connectionConfiguration = copyConnection()
        spec.monitoringConfiguration = monitoringConfig.copy()
    }

    /**
     * Applies the default configuration to a save step specification.
     */
    internal fun applyTo(spec: InfluxDbSaveStepSpecificationImpl<*>) {
        spec.connectionConfig = copyConnection()
        spec.monitoringConfig = monitoringConfig.copy()
    }

    /**
     * Applies the default configuration to a search step specification.
     */
    internal fun applyTo(spec: InfluxDbSearchStepSpecificationImpl<*>) {
        spec.connectionConfig = copyConnection()
        spec.monitoringConfig = monitoringConfig.copy()
    }
}

/**
 * Registers default connection and monitoring configuration for all InfluxDB steps in this scenario.
 *
 * Must be called before the step specifications that should use the defaults.
 *
 * @author Eric Jessé
 */
fun InfluxdbConfigurableScenarioSpecification.defaults(
    configurationBlock: InfluxDbDefaultsExtension.() -> Unit,
): ConfigurableScenarioSpecification {
    val spec = InfluxDbDefaultsExtensionImpl()
    spec.configurationBlock()
    (this.scenario as ExtensibleScenarioSpecification).addExtension(EXTENSION_NAME, spec)
    return this.scenario
}

/**
 * Registers default connection and monitoring configuration for all InfluxDB steps in this scenario.
 * When called on a step, the configuration is merged on top of any existing scenario-level defaults.
 *
 * Must be called before the step specifications that should use the defaults.
 *
 * @author Eric Jessé
 */
fun <INPUT, OUTPUT> InfluxdbStepSpecification<INPUT, OUTPUT, *>.defaults(
    configurationBlock: InfluxDbDefaultsExtension.() -> Unit,
): InfluxdbStepSpecification<INPUT, OUTPUT, *> {
    val spec = findInfluxDbDefaults(this as StepSpecification<*, *, *>) ?: InfluxDbDefaultsExtensionImpl()
    spec.configurationBlock()
    ((this as StepSpecification<*, *, *>).scenario as ExtensibleScenarioSpecification)
        .addExtension(EXTENSION_NAME, spec)
    return this
}

/**
 * Finds the [InfluxDbDefaultsExtensionImpl] registered in the scenario, if any.
 */
internal fun findInfluxDbDefaults(registry: StepSpecificationRegistry): InfluxDbDefaultsExtensionImpl? {
    return ((registry as InfluxdbScenarioSpecification).scenario as ExtensibleScenarioSpecification)
        .getExtension(EXTENSION_NAME)
}

/**
 * Finds the [InfluxDbDefaultsExtensionImpl] from a step specification's scenario.
 */
internal fun findInfluxDbDefaults(stepSpec: StepSpecification<*, *, *>): InfluxDbDefaultsExtensionImpl? {
    return (stepSpec.scenario as ExtensibleScenarioSpecification).getExtension(EXTENSION_NAME)
}
