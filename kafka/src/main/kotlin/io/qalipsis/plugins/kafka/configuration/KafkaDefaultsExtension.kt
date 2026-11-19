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

package io.qalipsis.plugins.kafka.configuration

import io.qalipsis.api.annotations.Spec
import io.qalipsis.api.scenario.ConfigurableScenarioSpecification
import io.qalipsis.api.scenario.ExtensibleScenarioSpecification
import io.qalipsis.api.scenario.StepSpecificationRegistry
import io.qalipsis.api.steps.StepMonitoringConfiguration
import io.qalipsis.api.steps.StepSpecification
import io.qalipsis.plugins.kafka.KafkaConfigurableScenarioSpecification
import io.qalipsis.plugins.kafka.KafkaScenarioSpecification
import io.qalipsis.plugins.kafka.KafkaStepSpecification
import io.qalipsis.plugins.kafka.consumer.KafkaConsumerStepSpecification
import io.qalipsis.plugins.kafka.producer.KafkaProducerStepSpecificationImpl

internal const val EXTENSION_NAME = "kafka.defaults"

/**
 * Public DSL interface for configuring Kafka default bootstrap, properties and monitoring.
 *
 * @author Eric Jessé
 */
interface KafkaDefaultsExtension {

    /**
     * Configures the default bootstrap hosts of the Kafka cluster for all sibling steps.
     */
    fun bootstrap(vararg hosts: String)

    /**
     * Configures the default additional properties for all sibling steps.
     */
    fun properties(vararg properties: Pair<String, Any>)

    /**
     * Configures the default additional properties for all sibling steps.
     */
    fun properties(properties: Map<String, Any>)

    /**
     * Configures the default monitoring for all sibling steps.
     */
    fun monitoring(monitoringConfig: StepMonitoringConfiguration.() -> Unit)
}

/**
 * Extension for the default bootstrap, properties and monitoring configuration of all Kafka steps in a scenario.
 *
 * When registered before the actual steps, the defaults are applied as pre-populated values.
 * Each step can still override any default value with its own explicit configuration.
 *
 * @author Eric Jessé
 */
@Spec
internal class KafkaDefaultsExtensionImpl : KafkaDefaultsExtension {

    internal var bootstrap: String = "localhost:9092"

    internal var properties: MutableMap<String, Any> = mutableMapOf()

    internal var monitoringConfig = StepMonitoringConfiguration().all()

    override fun bootstrap(vararg hosts: String) {
        bootstrap = hosts.joinToString(",")
    }

    override fun properties(vararg properties: Pair<String, Any>) {
        this.properties.putAll(properties)
    }

    override fun properties(properties: Map<String, Any>) {
        this.properties.putAll(properties)
    }

    override fun monitoring(monitoringConfig: StepMonitoringConfiguration.() -> Unit) {
        this.monitoringConfig.monitoringConfig()
    }

    /**
     * Applies the default configuration to a consumer step specification.
     */
    internal fun applyTo(spec: KafkaConsumerStepSpecification<*, *>) {
        spec.configuration.bootstrap = bootstrap
        spec.configuration.properties.putAll(properties)
        spec.monitoringConfig = monitoringConfig.copy()
    }

    /**
     * Applies the default configuration to a producer step specification.
     */
    internal fun applyTo(spec: KafkaProducerStepSpecificationImpl<*, *, *>) {
        spec.configuration.bootstrap = bootstrap
        spec.configuration.properties.putAll(properties)
        spec.monitoringConfig = monitoringConfig.copy()
    }
}

/**
 * Registers default bootstrap, properties and monitoring configuration for all Kafka steps in this scenario.
 *
 * Must be called before the step specifications that should use the defaults.
 *
 * @author Eric Jessé
 */
fun KafkaConfigurableScenarioSpecification.defaults(
    configurationBlock: KafkaDefaultsExtension.() -> Unit,
): ConfigurableScenarioSpecification {
    val spec = KafkaDefaultsExtensionImpl()
    spec.configurationBlock()
    (this.scenario as ExtensibleScenarioSpecification).addExtension(EXTENSION_NAME, spec)
    return this.scenario
}

/**
 * Registers default bootstrap, properties and monitoring configuration for all Kafka steps in this scenario.
 * When called on a step, the configuration is merged on top of any existing scenario-level defaults.
 *
 * Must be called before the step specifications that should use the defaults.
 *
 * @author Eric Jessé
 */
fun <I> KafkaStepSpecification<*, I, *>.defaults(
    configurationBlock: KafkaDefaultsExtension.() -> Unit,
): KafkaStepSpecification<*, I, *> {
    val spec = findKafkaDefaults(this) ?: KafkaDefaultsExtensionImpl()
    spec.configurationBlock()
    ((this as StepSpecification<*, *, *>).scenario as ExtensibleScenarioSpecification)
        .addExtension(EXTENSION_NAME, spec)
    return this
}

/**
 * Finds the [KafkaDefaultsExtensionImpl] registered in the scenario, if any.
 */
internal fun findKafkaDefaults(registry: StepSpecificationRegistry): KafkaDefaultsExtensionImpl? {
    return ((registry as KafkaScenarioSpecification).scenario as ExtensibleScenarioSpecification)
        .getExtension(EXTENSION_NAME)
}

/**
 * Finds the [KafkaDefaultsExtensionImpl] from a step specification's scenario.
 */
internal fun findKafkaDefaults(stepSpec: StepSpecification<*, *, *>): KafkaDefaultsExtensionImpl? {
    return (stepSpec.scenario as ExtensibleScenarioSpecification).getExtension(EXTENSION_NAME)
}
