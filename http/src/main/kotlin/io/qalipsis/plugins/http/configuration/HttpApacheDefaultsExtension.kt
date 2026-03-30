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

package io.qalipsis.plugins.http.configuration

import io.qalipsis.api.annotations.Spec
import io.qalipsis.api.scenario.ConfigurableScenarioSpecification
import io.qalipsis.api.scenario.ExtensibleScenarioSpecification
import io.qalipsis.api.scenario.StepSpecificationRegistry
import io.qalipsis.api.steps.StepMonitoringConfiguration
import io.qalipsis.api.steps.StepSpecification
import io.qalipsis.plugins.http.HttpApacheConfigurableScenarioSpecification
import io.qalipsis.plugins.http.HttpApachePluginSpecification
import io.qalipsis.plugins.http.HttpApacheScenarioSpecification
import io.qalipsis.plugins.http.HttpClientConfiguration
import io.qalipsis.plugins.http.HttpClientStepSpecificationImpl

internal const val EXTENSION_NAME = "http-apache.defaults"

/**
 * Public DSL interface for configuring HTTP Apache default connection, connection strategy, and monitoring.
 *
 * @author Eric Jessé
 */
interface HttpApacheDefaultsExtension {

    /**
     * Configures the default connection of the HTTP server for all sibling steps.
     */
    fun connect(configurationBlock: HttpClientConfiguration.() -> Unit)

    /**
     * Configures the default monitoring for all sibling steps.
     */
    fun monitoring(monitoringConfig: StepMonitoringConfiguration.() -> Unit)
}

/**
 * Specification for the default connection, connection strategy, and monitoring configuration
 * of all HTTP Apache steps in a scenario.
 *
 * When registered before the actual steps, the defaults are applied as pre-populated values.
 * Each step can still override any default value with its own explicit configuration.
 *
 * @author Eric Jessé
 */
@Spec
internal class HttpApacheDefaultsExtensionImpl : HttpApacheDefaultsExtension {

    internal var connectionConfiguration = HttpClientConfiguration()

    internal var monitoringConfig = StepMonitoringConfiguration().all()

    override fun connect(configurationBlock: HttpClientConfiguration.() -> Unit) {
        connectionConfiguration.configurationBlock()
    }

    override fun monitoring(monitoringConfig: StepMonitoringConfiguration.() -> Unit) {
        this.monitoringConfig.monitoringConfig()
    }

    /**
     * Applies the default configuration to an HTTP client step specification.
     *
     * Note: [HttpClientConfiguration.copy] (data class copy) is not sufficient because the
     * `init { url("http://localhost") }` block in [HttpClientConfiguration] re-runs during copy,
     * overwriting `scheme` and `contextPath` constructor params. Additionally, body properties
     * (`host`, `port`, `inetAddress`, `connectionStrategyConfiguration`) are not part of the
     * data class copy and are re-initialized to their default values.
     */
    internal fun applyTo(spec: HttpClientStepSpecificationImpl<*, *>) {
        val source = connectionConfiguration
        val copied = source.copy()
        // Restore constructor params overwritten by init { url("http://localhost") }.
        copied.scheme = source.scheme
        copied.contextPath = source.contextPath
        // Copy body properties not handled by data class copy().
        copied.host = source.host
        copied.port = source.port
        copied.inetAddress = source.inetAddress
        copied.connectionStrategyConfiguration = source.connectionStrategyConfiguration.copy()
        spec.connectionConfiguration = copied
        spec.monitoringConfig = monitoringConfig.copy()
    }
}

/**
 * Registers default connection, connection strategy, and monitoring configuration
 * for all HTTP Apache steps in this scenario.
 *
 * Must be called before the step specifications that should use the defaults.
 *
 * @author Eric Jessé
 */
fun HttpApacheConfigurableScenarioSpecification.defaults(
    configurationBlock: HttpApacheDefaultsExtension.() -> Unit,
): ConfigurableScenarioSpecification {
    val spec = HttpApacheDefaultsExtensionImpl()
    spec.configurationBlock()
    (this.scenario as ExtensibleScenarioSpecification).addExtension(EXTENSION_NAME, spec)
    return this.scenario
}

/**
 * Registers default connection, connection strategy, and monitoring configuration
 * for all HTTP Apache steps in this scenario.
 * The defaults are registered as a root step so they are discoverable by all sibling steps.
 *
 * Must be called before the step specifications that should use the defaults.
 *
 * @author Eric Jessé
 */
fun <I> HttpApachePluginSpecification<*, I, *>.defaults(
    configurationBlock: HttpApacheDefaultsExtension.() -> Unit,
): HttpApachePluginSpecification<*, I, *> {
    val spec = findHttpApacheDefaults(this) ?: HttpApacheDefaultsExtensionImpl()
    spec.configurationBlock()
    ((this as StepSpecification<*, *, *>).scenario as ExtensibleScenarioSpecification)
        .addExtension(EXTENSION_NAME, spec)
    return this
}

/**
 * Finds the [HttpApacheDefaultsExtensionImpl] registered in the scenario, if any.
 */
internal fun findHttpApacheDefaults(registry: StepSpecificationRegistry): HttpApacheDefaultsExtensionImpl? {
    return ((registry as HttpApacheScenarioSpecification).scenario as ExtensibleScenarioSpecification)
        .getExtension(EXTENSION_NAME)
}

/**
 * Finds the [HttpApacheDefaultsExtensionImpl] from a step specification's scenario.
 */
internal fun findHttpApacheDefaults(stepSpec: StepSpecification<*, *, *>): HttpApacheDefaultsExtensionImpl? {
    return (stepSpec.scenario as ExtensibleScenarioSpecification).getExtension(EXTENSION_NAME)
}
