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

package io.qalipsis.plugins.jakarta.configuration

import io.qalipsis.api.annotations.Spec
import io.qalipsis.api.scenario.ConfigurableScenarioSpecification
import io.qalipsis.api.scenario.ExtensibleScenarioSpecification
import io.qalipsis.api.scenario.StepSpecificationRegistry
import io.qalipsis.api.steps.StepMonitoringConfiguration
import io.qalipsis.api.steps.StepSpecification
import io.qalipsis.plugins.jakarta.JakartaConfigurableScenarioSpecification
import io.qalipsis.plugins.jakarta.JakartaScenarioSpecification
import io.qalipsis.plugins.jakarta.JakartaStepSpecification
import io.qalipsis.plugins.jakarta.consumer.JakartaConsumerStepSpecification
import io.qalipsis.plugins.jakarta.producer.JakartaProducerStepSpecificationImpl
import jakarta.jms.Connection
import jakarta.jms.QueueConnection
import jakarta.jms.Session
import jakarta.jms.TopicConnection

internal const val EXTENSION_NAME = "jakarta.defaults"

/**
 * Public DSL interface for configuring Jakarta EE Messaging default connections and monitoring.
 *
 * @author Eric Jesse
 */
interface JakartaDefaultsExtension {

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
     * Configures the default session factory for consumer and producer steps.
     */
    fun session(sessionFactory: (connection: Connection) -> Session)

    /**
     * Configures the default monitoring for all sibling Jakarta steps.
     */
    fun monitoring(monitoringConfig: StepMonitoringConfiguration.() -> Unit)
}

/**
 * Extension for the default connections and monitoring configuration of all Jakarta steps in a scenario.
 *
 * When registered before the actual steps, the defaults are applied as pre-populated values.
 * Each step can still override any default value with its own explicit configuration.
 *
 * @author Eric Jesse
 */
@Spec
internal class JakartaDefaultsExtensionImpl : JakartaDefaultsExtension {

    internal var queueConnectionFactory: (() -> QueueConnection)? = null

    internal var topicConnectionFactory: (() -> TopicConnection)? = null

    internal var connectionFactory: (() -> Connection)? = null

    internal var sessionFactory: ((connection: Connection) -> Session)? = null

    internal var monitoringConfig = StepMonitoringConfiguration().all()

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

    override fun session(sessionFactory: (connection: Connection) -> Session) {
        this.sessionFactory = sessionFactory
    }

    override fun monitoring(monitoringConfig: StepMonitoringConfiguration.() -> Unit) {
        this.monitoringConfig.monitoringConfig()
    }

    /**
     * Applies the default configuration to a consumer step specification.
     */
    internal fun applyTo(spec: JakartaConsumerStepSpecification<*>) {
        queueConnectionFactory?.let { spec.configuration.queueConnectionFactory = it }
        topicConnectionFactory?.let { spec.configuration.topicConnectionFactory = it }
        sessionFactory?.let { spec.configuration.sessionFactory = it }
        spec.monitoringConfig = monitoringConfig.copy()
    }

    /**
     * Applies the default configuration to a producer step specification.
     */
    internal fun applyTo(spec: JakartaProducerStepSpecificationImpl<*>) {
        connectionFactory?.let { spec.connectionFactory = it }
        sessionFactory?.let { spec.sessionFactory = it }
        spec.monitoringConfig = monitoringConfig.copy()
    }
}

/**
 * Registers default connections and monitoring configuration for all Jakarta steps in this scenario.
 *
 * Must be called before the step specifications that should use the defaults.
 *
 * @author Eric Jesse
 */
fun JakartaConfigurableScenarioSpecification.defaults(
    configurationBlock: JakartaDefaultsExtension.() -> Unit,
): ConfigurableScenarioSpecification {
    val spec = JakartaDefaultsExtensionImpl()
    spec.configurationBlock()
    (this.scenario as ExtensibleScenarioSpecification).addExtension(EXTENSION_NAME, spec)
    return this.scenario
}

/**
 * Registers default connections and monitoring configuration for all Jakarta steps in this scenario.
 * When called on a step, the configuration is merged on top of any existing scenario-level defaults.
 *
 * Must be called before the step specifications that should use the defaults.
 *
 * @author Eric Jesse
 */
fun <INPUT, OUTPUT> JakartaStepSpecification<INPUT, OUTPUT, *>.defaults(
    configurationBlock: JakartaDefaultsExtension.() -> Unit,
): JakartaStepSpecification<INPUT, OUTPUT, *> {
    val spec = findJakartaDefaults(this as StepSpecification<*, *, *>) ?: JakartaDefaultsExtensionImpl()
    spec.configurationBlock()
    ((this as StepSpecification<*, *, *>).scenario as ExtensibleScenarioSpecification)
        .addExtension(EXTENSION_NAME, spec)
    return this
}

/**
 * Finds the [JakartaDefaultsExtensionImpl] registered in the scenario, if any.
 */
internal fun findJakartaDefaults(registry: StepSpecificationRegistry): JakartaDefaultsExtensionImpl? {
    return ((registry as JakartaScenarioSpecification).scenario as ExtensibleScenarioSpecification)
        .getExtension(EXTENSION_NAME)
}

/**
 * Finds the [JakartaDefaultsExtensionImpl] from a step specification's scenario.
 */
internal fun findJakartaDefaults(stepSpec: StepSpecification<*, *, *>): JakartaDefaultsExtensionImpl? {
    return (stepSpec.scenario as ExtensibleScenarioSpecification).getExtension(EXTENSION_NAME)
}
