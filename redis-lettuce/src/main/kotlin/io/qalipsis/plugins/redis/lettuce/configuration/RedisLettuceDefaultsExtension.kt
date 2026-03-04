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

package io.qalipsis.plugins.redis.lettuce.configuration

import io.qalipsis.api.annotations.Spec
import io.qalipsis.api.scenario.ConfigurableScenarioSpecification
import io.qalipsis.api.scenario.ExtensibleScenarioSpecification
import io.qalipsis.api.scenario.StepSpecificationRegistry
import io.qalipsis.api.steps.StepMonitoringConfiguration
import io.qalipsis.api.steps.StepSpecification
import io.qalipsis.plugins.redis.lettuce.RedisLettuceConfigurableScenarioSpecification
import io.qalipsis.plugins.redis.lettuce.RedisLettuceScenarioSpecification
import io.qalipsis.plugins.redis.lettuce.RedisLettuceStepSpecification
import io.qalipsis.plugins.redis.lettuce.poll.LettucePollStepSpecificationImpl
import io.qalipsis.plugins.redis.lettuce.save.LettuceSaveStepSpecificationImpl
import io.qalipsis.plugins.redis.lettuce.streams.consumer.LettuceStreamsConsumerStepSpecificationImpl
import io.qalipsis.plugins.redis.lettuce.streams.producer.LettuceStreamsProducerStepSpecificationImpl

internal const val EXTENSION_NAME = "redis-lettuce.defaults"

/**
 * Public DSL interface for configuring Redis-Lettuce default connection and monitoring.
 *
 * @author Eric Jessé
 */
interface RedisLettuceDefaultsExtension {

    /**
     * Configures the default connection of the Redis server for all sibling steps.
     */
    fun connection(configurationBlock: RedisConnectionConfiguration.() -> Unit)

    /**
     * Configures the default monitoring for all sibling steps.
     */
    fun monitoring(monitoringConfig: StepMonitoringConfiguration.() -> Unit)
}

/**
 * Extension for the default connection and monitoring configuration of all Redis-Lettuce steps in a scenario.
 *
 * When registered before the actual steps, the defaults are applied as pre-populated values.
 * Each step can still override any default value with its own explicit configuration.
 *
 * @author Eric Jessé
 */
@Spec
internal class RedisLettuceDefaultsExtensionImpl : RedisLettuceDefaultsExtension {

    internal var connectionConfiguration = RedisConnectionConfiguration()

    internal var monitoringConfig = StepMonitoringConfiguration()

    override fun connection(configurationBlock: RedisConnectionConfiguration.() -> Unit) {
        connectionConfiguration.configurationBlock()
    }

    override fun monitoring(monitoringConfig: StepMonitoringConfiguration.() -> Unit) {
        this.monitoringConfig.monitoringConfig()
    }

    /**
     * Applies the default configuration to a poll step specification.
     */
    internal fun applyTo(spec: LettucePollStepSpecificationImpl<*>) {
        spec.connection = connectionConfiguration.copy()
        spec.monitoringConfig = monitoringConfig.copy()
    }

    /**
     * Applies the default configuration to a save step specification.
     */
    internal fun applyTo(spec: LettuceSaveStepSpecificationImpl<*>) {
        spec.connectionConfiguration = connectionConfiguration.copy()
        spec.monitoringConfig = monitoringConfig.copy()
    }

    /**
     * Applies the default configuration to a streams consumer step specification.
     */
    internal fun applyTo(spec: LettuceStreamsConsumerStepSpecificationImpl) {
        spec.connection = connectionConfiguration.copy()
        spec.monitoringConfig = monitoringConfig.copy()
    }

    /**
     * Applies the default configuration to a streams producer step specification.
     */
    internal fun applyTo(spec: LettuceStreamsProducerStepSpecificationImpl<*>) {
        spec.connection = connectionConfiguration.copy()
        spec.monitoringConfig = monitoringConfig.copy()
    }
}

/**
 * Registers default connection and monitoring configuration for all Redis-Lettuce steps in this scenario.
 *
 * Must be called before the step specifications that should use the defaults.
 *
 * @author Eric Jessé
 */
fun RedisLettuceConfigurableScenarioSpecification.defaults(
    configurationBlock: RedisLettuceDefaultsExtension.() -> Unit,
): ConfigurableScenarioSpecification {
    val spec = RedisLettuceDefaultsExtensionImpl()
    spec.configurationBlock()
    (this.scenario as ExtensibleScenarioSpecification).addExtension(EXTENSION_NAME, spec)
    return this.scenario
}

/**
 * Registers default connection and monitoring configuration for all Redis-Lettuce steps in this scenario.
 * When called on a step, the configuration is merged on top of any existing scenario-level defaults.
 *
 * Must be called before the step specifications that should use the defaults.
 *
 * @author Eric Jessé
 */
fun <INPUT, OUTPUT> RedisLettuceStepSpecification<INPUT, OUTPUT, *>.defaults(
    configurationBlock: RedisLettuceDefaultsExtension.() -> Unit,
): RedisLettuceStepSpecification<INPUT, OUTPUT, *> {
    val spec = findRedisLettuceDefaults(this) ?: RedisLettuceDefaultsExtensionImpl()
    spec.configurationBlock()
    ((this as StepSpecification<*, *, *>).scenario as ExtensibleScenarioSpecification)
        .addExtension(EXTENSION_NAME, spec)
    return this
}

/**
 * Finds the [RedisLettuceDefaultsExtensionImpl] registered in the scenario, if any.
 */
internal fun findRedisLettuceDefaults(registry: StepSpecificationRegistry): RedisLettuceDefaultsExtensionImpl? {
    return ((registry as RedisLettuceScenarioSpecification).scenario as ExtensibleScenarioSpecification)
        .getExtension(EXTENSION_NAME)
}

/**
 * Finds the [RedisLettuceDefaultsExtensionImpl] from a step specification's scenario.
 */
internal fun findRedisLettuceDefaults(stepSpec: StepSpecification<*, *, *>): RedisLettuceDefaultsExtensionImpl? {
    return (stepSpec.scenario as ExtensibleScenarioSpecification).getExtension(EXTENSION_NAME)
}
