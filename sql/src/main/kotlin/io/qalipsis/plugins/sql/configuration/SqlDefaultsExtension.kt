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

package io.qalipsis.plugins.sql.configuration

import io.qalipsis.api.annotations.Spec
import io.qalipsis.api.scenario.ConfigurableScenarioSpecification
import io.qalipsis.api.scenario.ExtensibleScenarioSpecification
import io.qalipsis.api.scenario.StepSpecificationRegistry
import io.qalipsis.api.steps.StepMonitoringConfiguration
import io.qalipsis.api.steps.StepSpecification
import io.qalipsis.plugins.sql.SqlConfigurableScenarioSpecification
import io.qalipsis.plugins.sql.SqlConnection
import io.qalipsis.plugins.sql.SqlScenarioSpecification
import io.qalipsis.plugins.sql.SqlStepSpecification
import io.qalipsis.plugins.sql.dialect.Protocol
import io.qalipsis.plugins.sql.poll.SqlPollStepSpecificationImpl
import io.qalipsis.plugins.sql.save.SqlSaveStepSpecificationImpl
import io.qalipsis.plugins.sql.search.SqlSearchStepSpecificationImpl

internal const val EXTENSION_NAME = "sql.defaults"

/**
 * Public DSL interface for configuring SQL default connection, protocol and monitoring.
 *
 * @author Eric Jessé
 */
interface SqlDefaultsExtension {

    /**
     * Configures the default connection for all sibling SQL steps.
     */
    fun connection(configBlock: SqlConnection.() -> Unit)

    /**
     * Configures the default protocol for all sibling SQL steps.
     */
    fun protocol(protocol: Protocol)

    /**
     * Configures the default monitoring for all sibling SQL steps.
     */
    fun monitoring(monitoringConfig: StepMonitoringConfiguration.() -> Unit)
}

/**
 * Extension for the default connection, protocol and monitoring configuration of all SQL steps in a scenario.
 *
 * When registered before the actual steps, the defaults are applied as pre-populated values.
 * Each step can still override any default value with its own explicit configuration.
 *
 * @author Eric Jessé
 */
@Spec
internal class SqlDefaultsExtensionImpl : SqlDefaultsExtension {

    internal var connectionConfiguration = SqlConnection()

    internal var protocol: Protocol? = null

    internal var monitoringConfig = StepMonitoringConfiguration()

    override fun connection(configBlock: SqlConnection.() -> Unit) {
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
    internal fun applyTo(spec: SqlPollStepSpecificationImpl) {
        spec.connection = connectionConfiguration.copy()
        protocol?.let { spec.protocol = it }
        spec.monitoringConfig = monitoringConfig.copy()
    }

    /**
     * Applies the default configuration to a save step specification.
     */
    internal fun applyTo(spec: SqlSaveStepSpecificationImpl<*>) {
        spec.connection = connectionConfiguration.copy()
        protocol?.let { spec.protocol = it }
        spec.monitoringConfig = monitoringConfig.copy()
    }

    /**
     * Applies the default configuration to a search step specification.
     */
    internal fun applyTo(spec: SqlSearchStepSpecificationImpl<*>) {
        spec.connection = connectionConfiguration.copy()
        protocol?.let { spec.protocol = it }
        spec.monitoringConfig = monitoringConfig.copy()
    }
}

/**
 * Registers default connection, protocol and monitoring configuration for all SQL steps in this scenario.
 *
 * Must be called before the step specifications that should use the defaults.
 *
 * @author Eric Jessé
 */
fun SqlConfigurableScenarioSpecification.defaults(
    configurationBlock: SqlDefaultsExtension.() -> Unit,
): ConfigurableScenarioSpecification {
    val spec = SqlDefaultsExtensionImpl()
    spec.configurationBlock()
    (this.scenario as ExtensibleScenarioSpecification).addExtension(EXTENSION_NAME, spec)
    return this.scenario
}

/**
 * Registers default connection, protocol and monitoring configuration for all SQL steps in this scenario.
 * When called on a step, the configuration is merged on top of any existing scenario-level defaults.
 *
 * Must be called before the step specifications that should use the defaults.
 *
 * @author Eric Jessé
 */
fun <INPUT, OUTPUT> SqlStepSpecification<INPUT, OUTPUT, *>.defaults(
    configurationBlock: SqlDefaultsExtension.() -> Unit,
): SqlStepSpecification<INPUT, OUTPUT, *> {
    val spec = findSqlDefaults(this as StepSpecification<*, *, *>) ?: SqlDefaultsExtensionImpl()
    spec.configurationBlock()
    ((this as StepSpecification<*, *, *>).scenario as ExtensibleScenarioSpecification)
        .addExtension(EXTENSION_NAME, spec)
    return this
}

/**
 * Finds the [SqlDefaultsExtensionImpl] registered in the scenario, if any.
 */
internal fun findSqlDefaults(registry: StepSpecificationRegistry): SqlDefaultsExtensionImpl? {
    return ((registry as SqlScenarioSpecification).scenario as ExtensibleScenarioSpecification)
        .getExtension(EXTENSION_NAME)
}

/**
 * Finds the [SqlDefaultsExtensionImpl] from a step specification's scenario.
 */
internal fun findSqlDefaults(stepSpec: StepSpecification<*, *, *>): SqlDefaultsExtensionImpl? {
    return (stepSpec.scenario as ExtensibleScenarioSpecification).getExtension(EXTENSION_NAME)
}
