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

package io.qalipsis.plugins.r2dbc.jasync.configuration

import io.qalipsis.api.annotations.Spec
import io.qalipsis.api.scenario.ConfigurableScenarioSpecification
import io.qalipsis.api.scenario.ExtensibleScenarioSpecification
import io.qalipsis.api.scenario.StepSpecificationRegistry
import io.qalipsis.api.steps.StepMonitoringConfiguration
import io.qalipsis.api.steps.StepSpecification
import io.qalipsis.plugins.r2dbc.jasync.JasyncConnection
import io.qalipsis.plugins.r2dbc.jasync.R2dbcJasyncConfigurableScenarioSpecification
import io.qalipsis.plugins.r2dbc.jasync.R2dbcJasyncScenarioSpecification
import io.qalipsis.plugins.r2dbc.jasync.R2dbcJasyncStepSpecification
import io.qalipsis.plugins.r2dbc.jasync.dialect.Protocol
import io.qalipsis.plugins.r2dbc.jasync.poll.JasyncPollStepSpecificationImpl
import io.qalipsis.plugins.r2dbc.jasync.save.JasyncSaveStepSpecificationImpl
import io.qalipsis.plugins.r2dbc.jasync.search.JasyncSearchStepSpecificationImpl

internal const val EXTENSION_NAME = "r2dbc-jasync.defaults"

/**
 * Public DSL interface for configuring R2DBC-Jasync default connection, protocol and monitoring.
 *
 * @author Eric Jessé
 */
interface R2dbcJasyncDefaultsExtension {

    /**
     * Configures the default connection for all sibling R2DBC-Jasync steps.
     */
    fun connection(configBlock: JasyncConnection.() -> Unit)

    /**
     * Configures the default protocol for all sibling R2DBC-Jasync steps.
     */
    fun protocol(protocol: Protocol)

    /**
     * Configures the default monitoring for all sibling R2DBC-Jasync steps.
     */
    fun monitoring(monitoringConfig: StepMonitoringConfiguration.() -> Unit)
}

/**
 * Extension for the default connection, protocol and monitoring configuration of all R2DBC-Jasync steps in a scenario.
 *
 * When registered before the actual steps, the defaults are applied as pre-populated values.
 * Each step can still override any default value with its own explicit configuration.
 *
 * @author Eric Jessé
 */
@Spec
internal class R2dbcJasyncDefaultsExtensionImpl : R2dbcJasyncDefaultsExtension {

    internal var connectionConfiguration = JasyncConnection()

    internal var protocol: Protocol? = null

    internal var monitoringConfig = StepMonitoringConfiguration().all()

    override fun connection(configBlock: JasyncConnection.() -> Unit) {
        connectionConfiguration.configBlock()
    }

    override fun protocol(protocol: Protocol) {
        this.protocol = protocol
    }

    override fun monitoring(monitoringConfig: StepMonitoringConfiguration.() -> Unit) {
        this.monitoringConfig.monitoringConfig()
    }

    /**
     * Applies the default configuration to a poll step specification.
     */
    internal fun applyTo(spec: JasyncPollStepSpecificationImpl) {
        spec.connection = connectionConfiguration.copy()
        protocol?.let { spec.protocol = it }
        spec.monitoringConfig = monitoringConfig.copy()
    }

    /**
     * Applies the default configuration to a save step specification.
     */
    internal fun applyTo(spec: JasyncSaveStepSpecificationImpl<*>) {
        spec.connection = connectionConfiguration.copy()
        protocol?.let { spec.protocol = it }
        spec.monitoringConfig = monitoringConfig.copy()
    }

    /**
     * Applies the default configuration to a search step specification.
     */
    internal fun applyTo(spec: JasyncSearchStepSpecificationImpl<*>) {
        spec.connection = connectionConfiguration.copy()
        protocol?.let { spec.protocol = it }
        spec.monitoringConfig = monitoringConfig.copy()
    }
}

/**
 * Registers default connection, protocol and monitoring configuration for all R2DBC-Jasync steps in this scenario.
 *
 * Must be called before the step specifications that should use the defaults.
 *
 * @author Eric Jessé
 */
fun R2dbcJasyncConfigurableScenarioSpecification.defaults(
    configurationBlock: R2dbcJasyncDefaultsExtension.() -> Unit,
): ConfigurableScenarioSpecification {
    val spec = R2dbcJasyncDefaultsExtensionImpl()
    spec.configurationBlock()
    (this.scenario as ExtensibleScenarioSpecification).addExtension(EXTENSION_NAME, spec)
    return this.scenario
}

/**
 * Registers default connection, protocol and monitoring configuration for all R2DBC-Jasync steps in this scenario.
 * When called on a step, the configuration is merged on top of any existing scenario-level defaults.
 *
 * Must be called before the step specifications that should use the defaults.
 *
 * @author Eric Jessé
 */
fun <INPUT, OUTPUT> R2dbcJasyncStepSpecification<INPUT, OUTPUT, *>.defaults(
    configurationBlock: R2dbcJasyncDefaultsExtension.() -> Unit,
): R2dbcJasyncStepSpecification<INPUT, OUTPUT, *> {
    val spec = findR2dbcJasyncDefaults(this as StepSpecification<*, *, *>) ?: R2dbcJasyncDefaultsExtensionImpl()
    spec.configurationBlock()
    ((this as StepSpecification<*, *, *>).scenario as ExtensibleScenarioSpecification)
        .addExtension(EXTENSION_NAME, spec)
    return this
}

/**
 * Finds the [R2dbcJasyncDefaultsExtensionImpl] registered in the scenario, if any.
 */
internal fun findR2dbcJasyncDefaults(registry: StepSpecificationRegistry): R2dbcJasyncDefaultsExtensionImpl? {
    return ((registry as R2dbcJasyncScenarioSpecification).scenario as ExtensibleScenarioSpecification)
        .getExtension(EXTENSION_NAME)
}

/**
 * Finds the [R2dbcJasyncDefaultsExtensionImpl] from a step specification's scenario.
 */
internal fun findR2dbcJasyncDefaults(stepSpec: StepSpecification<*, *, *>): R2dbcJasyncDefaultsExtensionImpl? {
    return (stepSpec.scenario as ExtensibleScenarioSpecification).getExtension(EXTENSION_NAME)
}
