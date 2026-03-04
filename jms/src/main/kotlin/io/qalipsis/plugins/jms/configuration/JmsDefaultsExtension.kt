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

package io.qalipsis.plugins.jms.configuration

import io.qalipsis.api.annotations.Spec
import io.qalipsis.api.scenario.ConfigurableScenarioSpecification
import io.qalipsis.api.scenario.ExtensibleScenarioSpecification
import io.qalipsis.api.scenario.StepSpecificationRegistry
import io.qalipsis.api.steps.StepMonitoringConfiguration
import io.qalipsis.api.steps.StepSpecification
import io.qalipsis.plugins.jms.JmsConfigurableScenarioSpecification
import io.qalipsis.plugins.jms.JmsScenarioSpecification
import io.qalipsis.plugins.jms.JmsStepSpecification
import io.qalipsis.plugins.jms.consumer.JmsConsumerStepSpecification
import io.qalipsis.plugins.jms.producer.JmsProducerStepSpecificationImpl
import javax.jms.Connection
import javax.jms.QueueConnection
import javax.jms.TopicConnection

internal const val EXTENSION_NAME = "jms.defaults"

/**
 * Public DSL interface for configuring JMS default connections and monitoring.
 *
 * @author Eric Jesse
 */
interface JmsDefaultsExtension {

    /**
     * Configures the default queue connection factory for consumer steps.
     */
    fun queueConnection(queueConnectionFactory: () -> QueueConnection)

    /**
     * Configures the default topic connection factory for consumer steps.
     */
    fun topicConnection(topicConnectionFactory: () -> TopicConnection)

    /**
     * Configures the default connection factory for producer steps.
     */
    fun connection(connectionFactory: () -> Connection)

    /**
     * Configures the default monitoring for all sibling JMS steps.
     */
    fun monitoring(monitoringConfig: StepMonitoringConfiguration.() -> Unit)
}

/**
 * Extension for the default connections and monitoring configuration of all JMS steps in a scenario.
 *
 * When registered before the actual steps, the defaults are applied as pre-populated values.
 * Each step can still override any default value with its own explicit configuration.
 *
 * @author Eric Jesse
 */
@Spec
internal class JmsDefaultsExtensionImpl : JmsDefaultsExtension {

    internal var queueConnectionFactory: (() -> QueueConnection)? = null

    internal var topicConnectionFactory: (() -> TopicConnection)? = null

    internal var connectionFactory: (() -> Connection)? = null

    internal var monitoringConfig = StepMonitoringConfiguration()

    override fun queueConnection(queueConnectionFactory: () -> QueueConnection) {
        this.queueConnectionFactory = queueConnectionFactory
        this.topicConnectionFactory = null
    }

    override fun topicConnection(topicConnectionFactory: () -> TopicConnection) {
        this.topicConnectionFactory = topicConnectionFactory
        this.queueConnectionFactory = null
    }

    override fun connection(connectionFactory: () -> Connection) {
        this.connectionFactory = connectionFactory
    }

    override fun monitoring(monitoringConfig: StepMonitoringConfiguration.() -> Unit) {
        this.monitoringConfig.monitoringConfig()
    }

    /**
     * Applies the default configuration to a consumer step specification.
     */
    internal fun applyTo(spec: JmsConsumerStepSpecification<*>) {
        queueConnectionFactory?.let { spec.configuration.queueConnectionFactory = it }
        topicConnectionFactory?.let { spec.configuration.topicConnectionFactory = it }
        spec.monitoringConfig = monitoringConfig.copy()
    }

    /**
     * Applies the default configuration to a producer step specification.
     */
    internal fun applyTo(spec: JmsProducerStepSpecificationImpl<*>) {
        connectionFactory?.let { spec.connectionFactory = it }
        spec.monitoringConfig = monitoringConfig.copy()
    }
}

/**
 * Registers default connections and monitoring configuration for all JMS steps in this scenario.
 *
 * Must be called before the step specifications that should use the defaults.
 *
 * @author Eric Jesse
 */
fun JmsConfigurableScenarioSpecification.defaults(
    configurationBlock: JmsDefaultsExtension.() -> Unit,
): ConfigurableScenarioSpecification {
    val spec = JmsDefaultsExtensionImpl()
    spec.configurationBlock()
    (this.scenario as ExtensibleScenarioSpecification).addExtension(EXTENSION_NAME, spec)
    return this.scenario
}

/**
 * Registers default connections and monitoring configuration for all JMS steps in this scenario.
 * When called on a step, the configuration is merged on top of any existing scenario-level defaults.
 *
 * Must be called before the step specifications that should use the defaults.
 *
 * @author Eric Jesse
 */
fun <INPUT, OUTPUT> JmsStepSpecification<INPUT, OUTPUT, *>.defaults(
    configurationBlock: JmsDefaultsExtension.() -> Unit,
): JmsStepSpecification<INPUT, OUTPUT, *> {
    val spec = findJmsDefaults(this as StepSpecification<*, *, *>) ?: JmsDefaultsExtensionImpl()
    spec.configurationBlock()
    ((this as StepSpecification<*, *, *>).scenario as ExtensibleScenarioSpecification)
        .addExtension(EXTENSION_NAME, spec)
    return this
}

/**
 * Finds the [JmsDefaultsExtensionImpl] registered in the scenario, if any.
 */
internal fun findJmsDefaults(registry: StepSpecificationRegistry): JmsDefaultsExtensionImpl? {
    return ((registry as JmsScenarioSpecification).scenario as ExtensibleScenarioSpecification)
        .getExtension(EXTENSION_NAME)
}

/**
 * Finds the [JmsDefaultsExtensionImpl] from a step specification's scenario.
 */
internal fun findJmsDefaults(stepSpec: StepSpecification<*, *, *>): JmsDefaultsExtensionImpl? {
    return (stepSpec.scenario as ExtensibleScenarioSpecification).getExtension(EXTENSION_NAME)
}
