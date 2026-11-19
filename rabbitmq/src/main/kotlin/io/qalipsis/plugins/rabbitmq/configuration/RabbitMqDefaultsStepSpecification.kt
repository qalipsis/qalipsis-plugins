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

package io.qalipsis.plugins.rabbitmq.configuration

import io.qalipsis.api.annotations.Spec
import io.qalipsis.api.scenario.ConfigurableScenarioSpecification
import io.qalipsis.api.scenario.ExtensibleScenarioSpecification
import io.qalipsis.api.scenario.StepSpecificationRegistry
import io.qalipsis.api.steps.StepMonitoringConfiguration
import io.qalipsis.api.steps.StepSpecification
import io.qalipsis.plugins.rabbitmq.RabbitMqConfigurableScenarioSpecification
import io.qalipsis.plugins.rabbitmq.RabbitMqScenarioSpecification
import io.qalipsis.plugins.rabbitmq.RabbitMqStepSpecification
import io.qalipsis.plugins.rabbitmq.consumer.RabbitMqConsumerStepSpecificationImpl
import io.qalipsis.plugins.rabbitmq.producer.RabbitMqProducerStepSpecificationImpl

internal const val EXTENSION_NAME = "rabbitmq.defaults"

/**
 * Public DSL interface for configuring RabbitMQ default connection and monitoring.
 *
 * @author Eric Jessé
 */
interface RabbitMqDefaultsExtension {

    /**
     * Configures the default connection of the RabbitMQ broker for all sibling steps.
     */
    fun connection(configurationBlock: RabbitMqConnectionConfiguration.() -> Unit)

    /**
     * Configures the default monitoring for all sibling steps.
     */
    fun monitoring(monitoringConfig: StepMonitoringConfiguration.() -> Unit)
}

/**
 * Specification for the default connection and monitoring configuration of all RabbitMQ steps in a scenario.
 *
 * When registered before the actual steps, the defaults are applied as pre-populated values.
 * Each step can still override any default value with its own explicit configuration.
 *
 * @author Eric Jessé
 */
@Spec
internal class RabbitMqDefaultsExtensionImpl : RabbitMqDefaultsExtension {

    internal var connectionConfiguration = RabbitMqConnectionConfiguration()

    internal var monitoringConfig = StepMonitoringConfiguration().all()

    override fun connection(configurationBlock: RabbitMqConnectionConfiguration.() -> Unit) {
        connectionConfiguration.configurationBlock()
    }

    override fun monitoring(monitoringConfig: StepMonitoringConfiguration.() -> Unit) {
        this.monitoringConfig.monitoringConfig()
    }

    /**
     * Applies the default connection configuration to a consumer step specification.
     */
    internal fun applyTo(spec: RabbitMqConsumerStepSpecificationImpl<*>) {
        spec.connectionConfiguration = connectionConfiguration.copy()
        spec.monitoringConfig = monitoringConfig.copy()
    }

    /**
     * Applies the default connection configuration to a producer step specification.
     */
    internal fun applyTo(spec: RabbitMqProducerStepSpecificationImpl<*>) {
        spec.connectionConfiguration = connectionConfiguration.copy()
        spec.monitoring = monitoringConfig.copy()
    }
}

/**
 * Registers default connection and monitoring configuration for all RabbitMQ steps in this scenario.
 *
 * Must be called before the step specifications that should use the defaults.
 *
 * @author Eric Jessé
 */
fun RabbitMqConfigurableScenarioSpecification.defaults(
    configurationBlock: RabbitMqDefaultsExtension.() -> Unit,
): ConfigurableScenarioSpecification {
    val spec = RabbitMqDefaultsExtensionImpl()
    spec.configurationBlock()
    (this.scenario as ExtensibleScenarioSpecification).addExtension(EXTENSION_NAME, spec)
    return this.scenario as ConfigurableScenarioSpecification
}

/**
 * Registers default connection and monitoring configuration for all RabbitMQ steps in this scenario.
 * The defaults are registered as a root step so they are discoverable by all sibling steps.
 *
 * Must be called before the step specifications that should use the defaults.
 *
 * @author Eric Jessé
 */
fun <I> RabbitMqStepSpecification<*, I, *>.defaults(
    configurationBlock: RabbitMqDefaultsExtension.() -> Unit,
): RabbitMqStepSpecification<*, I, *> {
    val spec = findRabbitMqDefaults(this) ?: RabbitMqDefaultsExtensionImpl()
    spec.configurationBlock()
    // Register as root step so it's discoverable by all sibling steps.
    ((this as StepSpecification<*, *, *>).scenario as ExtensibleScenarioSpecification)
        .addExtension(EXTENSION_NAME, spec)
    return this
}

/**
 * Finds the [RabbitMqDefaultsStepSpecification] registered in the scenario, if any.
 */
internal fun findRabbitMqDefaults(registry: StepSpecificationRegistry): RabbitMqDefaultsExtensionImpl? {
    return ((registry as RabbitMqScenarioSpecification).scenario as ExtensibleScenarioSpecification)
        .getExtension(EXTENSION_NAME)
}

/**
 * Finds the [RabbitMqDefaultsStepSpecification] from a step specification's scenario.
 */
internal fun findRabbitMqDefaults(stepSpec: StepSpecification<*, *, *>): RabbitMqDefaultsExtensionImpl? {
    return (stepSpec.scenario as ExtensibleScenarioSpecification).getExtension(EXTENSION_NAME)
}
