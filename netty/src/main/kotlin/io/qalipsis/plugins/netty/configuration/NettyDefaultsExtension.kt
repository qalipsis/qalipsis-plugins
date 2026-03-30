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

package io.qalipsis.plugins.netty.configuration

import io.qalipsis.api.annotations.Spec
import io.qalipsis.api.scenario.ConfigurableScenarioSpecification
import io.qalipsis.api.scenario.ExtensibleScenarioSpecification
import io.qalipsis.api.scenario.StepSpecificationRegistry
import io.qalipsis.api.steps.StepMonitoringConfiguration
import io.qalipsis.api.steps.StepSpecification
import io.qalipsis.plugins.netty.NettyConfigurableScenarioSpecification
import io.qalipsis.plugins.netty.NettyPluginSpecification
import io.qalipsis.plugins.netty.NettyScenarioSpecification
import io.qalipsis.plugins.netty.http.spec.HttpClientConfiguration
import io.qalipsis.plugins.netty.http.spec.HttpClientStepSpecificationImpl
import io.qalipsis.plugins.netty.mqtt.publisher.spec.MqttPublishStepSpecificationImpl
import io.qalipsis.plugins.netty.mqtt.spec.MqttConnectionConfiguration
import io.qalipsis.plugins.netty.mqtt.subscriber.spec.MqttSubscribeStepSpecificationImpl
import io.qalipsis.plugins.netty.tcp.spec.TcpClientConfiguration
import io.qalipsis.plugins.netty.tcp.spec.TcpClientStepSpecificationImpl
import io.qalipsis.plugins.netty.udp.spec.UdpClientStepSpecification

internal const val EXTENSION_NAME = "netty.defaults"

/**
 * Public DSL interface for configuring Netty default connections and monitoring.
 *
 * @author Eric Jesse
 */
interface NettyDefaultsExtension {

    /**
     * Configures the default TCP connection for TCP steps.
     */
    fun tcpConnection(configurationBlock: TcpClientConfiguration.() -> Unit)

    /**
     * Configures the default HTTP connection for HTTP steps.
     */
    fun httpConnection(configurationBlock: HttpClientConfiguration.() -> Unit)

    /**
     * Configures the default UDP connection for UDP steps.
     */
    fun udpConnection(configurationBlock: ConnectionConfiguration.() -> Unit)

    /**
     * Configures the default MQTT connection for MQTT publish and subscribe steps.
     */
    fun mqttConnection(configurationBlock: MqttConnectionConfiguration.() -> Unit)

    /**
     * Configures the default monitoring for all sibling Netty steps.
     */
    fun monitoring(monitoringConfig: StepMonitoringConfiguration.() -> Unit)
}

/**
 * Extension for the default connections and monitoring configuration of all Netty steps in a scenario.
 *
 * When registered before the actual steps, the defaults are applied as pre-populated values.
 * Each step can still override any default value with its own explicit configuration.
 *
 * @author Eric Jesse
 */
@Spec
internal class NettyDefaultsExtensionImpl : NettyDefaultsExtension {

    internal var tcpConnectionConfig: (TcpClientConfiguration.() -> Unit)? = null

    internal var httpConnectionConfig: (HttpClientConfiguration.() -> Unit)? = null

    internal var udpConnectionConfig: (ConnectionConfiguration.() -> Unit)? = null

    internal var mqttConnectionConfig: (MqttConnectionConfiguration.() -> Unit)? = null

    internal var monitoringConfig = StepMonitoringConfiguration().all()

    override fun tcpConnection(configurationBlock: TcpClientConfiguration.() -> Unit) {
        this.tcpConnectionConfig = configurationBlock
    }

    override fun httpConnection(configurationBlock: HttpClientConfiguration.() -> Unit) {
        this.httpConnectionConfig = configurationBlock
    }

    override fun udpConnection(configurationBlock: ConnectionConfiguration.() -> Unit) {
        this.udpConnectionConfig = configurationBlock
    }

    override fun mqttConnection(configurationBlock: MqttConnectionConfiguration.() -> Unit) {
        this.mqttConnectionConfig = configurationBlock
    }

    override fun monitoring(monitoringConfig: StepMonitoringConfiguration.() -> Unit) {
        this.monitoringConfig.monitoringConfig()
    }

    /**
     * Applies the default configuration to a TCP step specification.
     */
    internal fun applyTo(spec: TcpClientStepSpecificationImpl<*>) {
        tcpConnectionConfig?.let { spec.connectionConfiguration.it() }
        spec.monitoringConfiguration = monitoringConfig.copy()
    }

    /**
     * Applies the default configuration to an HTTP step specification.
     */
    internal fun applyTo(spec: HttpClientStepSpecificationImpl<*, *>) {
        httpConnectionConfig?.let { spec.connectionConfiguration.it() }
        spec.monitoringConfiguration = monitoringConfig.copy()
    }

    /**
     * Applies the default configuration to a UDP step specification.
     */
    internal fun applyTo(spec: UdpClientStepSpecification<*>) {
        udpConnectionConfig?.let { spec.connectionConfiguration.it() }
        spec.monitoringConfiguration = monitoringConfig.copy()
    }

    /**
     * Applies the default configuration to a MQTT publish step specification.
     */
    internal fun applyTo(spec: MqttPublishStepSpecificationImpl<*>) {
        mqttConnectionConfig?.let { spec.mqttPublishConfiguration.connectionConfiguration.it() }
        spec.monitoringConfig = monitoringConfig.copy()
    }

    /**
     * Applies the default configuration to a MQTT subscribe step specification.
     */
    internal fun applyTo(spec: MqttSubscribeStepSpecificationImpl<*>) {
        mqttConnectionConfig?.let { spec.mqttSubscribeConfiguration.connectionConfiguration.it() }
        spec.monitoringConfig = monitoringConfig.copy()
    }
}

/**
 * Registers default connections and monitoring configuration for all Netty steps in this scenario.
 *
 * Must be called before the step specifications that should use the defaults.
 *
 * @author Eric Jesse
 */
fun NettyConfigurableScenarioSpecification.defaults(
    configurationBlock: NettyDefaultsExtension.() -> Unit,
): ConfigurableScenarioSpecification {
    val spec = NettyDefaultsExtensionImpl()
    spec.configurationBlock()
    (this.scenario as ExtensibleScenarioSpecification).addExtension(EXTENSION_NAME, spec)
    return this.scenario
}

/**
 * Registers default connections and monitoring configuration for all Netty steps in this scenario.
 * When called on a step, the configuration is merged on top of any existing scenario-level defaults.
 *
 * Must be called before the step specifications that should use the defaults.
 *
 * @author Eric Jesse
 */
fun <INPUT, OUTPUT> NettyPluginSpecification<INPUT, OUTPUT, *>.defaults(
    configurationBlock: NettyDefaultsExtension.() -> Unit,
): NettyPluginSpecification<INPUT, OUTPUT, *> {
    val spec = findNettyDefaults(this as StepSpecification<*, *, *>) ?: NettyDefaultsExtensionImpl()
    spec.configurationBlock()
    ((this as StepSpecification<*, *, *>).scenario as ExtensibleScenarioSpecification)
        .addExtension(EXTENSION_NAME, spec)
    return this
}

/**
 * Finds the [NettyDefaultsExtensionImpl] registered in the scenario, if any.
 */
internal fun findNettyDefaults(registry: StepSpecificationRegistry): NettyDefaultsExtensionImpl? {
    return ((registry as NettyScenarioSpecification).scenario as ExtensibleScenarioSpecification)
        .getExtension(EXTENSION_NAME)
}

/**
 * Finds the [NettyDefaultsExtensionImpl] from a step specification's scenario.
 */
internal fun findNettyDefaults(stepSpec: StepSpecification<*, *, *>): NettyDefaultsExtensionImpl? {
    return (stepSpec.scenario as ExtensibleScenarioSpecification).getExtension(EXTENSION_NAME)
}
