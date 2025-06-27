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

package io.qalipsis.plugins.redis.lettuce.poll

import io.qalipsis.api.annotations.Spec
import io.qalipsis.api.constraints.PositiveOrZeroDuration
import io.qalipsis.api.scenario.StepSpecificationRegistry
import io.qalipsis.api.steps.AbstractStepSpecification
import io.qalipsis.api.steps.BroadcastSpecification
import io.qalipsis.api.steps.ConfigurableStepSpecification
import io.qalipsis.api.steps.LoopableSpecification
import io.qalipsis.api.steps.SingletonConfiguration
import io.qalipsis.api.steps.SingletonType
import io.qalipsis.api.steps.StepMonitoringConfiguration
import io.qalipsis.api.steps.StepSpecification
import io.qalipsis.api.steps.UnicastSpecification
import io.qalipsis.plugins.redis.lettuce.RedisLettuceScenarioSpecification
import io.qalipsis.plugins.redis.lettuce.RedisRecord
import io.qalipsis.plugins.redis.lettuce.configuration.RedisConnectionConfiguration
import io.qalipsis.plugins.redis.lettuce.configuration.RedisConnectionType.CLUSTER
import io.qalipsis.plugins.redis.lettuce.configuration.RedisConnectionType.SENTINEL
import io.qalipsis.plugins.redis.lettuce.configuration.RedisConnectionType.SINGLE
import java.time.Duration
import java.time.temporal.ChronoUnit
import javax.validation.constraints.NotBlank

/**
 * Specification for an [io.qalipsis.api.steps.datasource.IterativeDatasourceStep] to poll data from a Redis database.
 *
 * The output is a list of [RedisRecord] containing the deserialized value for each specific method.
 *
 * When [flatten] is called, the records are provided one by one to the next step, otherwise each poll batch remains complete.
 *
 * @author Gabriel Moraes
 */
@Spec
interface LettucePollStepSpecification<V : Any> :
    StepSpecification<Unit, LettucePollResult<V>, LettucePollStepSpecification<V>>,
    ConfigurableStepSpecification<Unit, LettucePollResult<V>, LettucePollStepSpecification<V>>,
    LoopableSpecification, UnicastSpecification, BroadcastSpecification {

    /**
     * Configures the connection to the database.
     *
     * The connection type is specified in the [configBlock] by using either [SINGLE], [CLUSTER] or [SENTINEL].
     * It is also possible to connect to more than one node, this can be achieved by passing a more than one
     * parameter in the [List] of nodes for the [configBlock].
     *
     * For the [SENTINEL] connection type is required to pass a value for the masterId parameter.
     */
    fun connection(configBlock: RedisConnectionConfiguration.() -> Unit)

    /**
     * Configures the patterns of the keys to scan when using [pollScan] or the key to scan when using [pollHscan], [pollSscan] or [pollZscan].
     */
    fun keyOrPattern(@NotBlank keyOrPattern: String)

    /**
     * Delay between two executions of poll.
     *
     * @param delay the delay to wait between the end of a poll and start of next one
     */
    fun pollDelay(delay: Duration)

    /**
     * Delay between two executions of poll.
     *
     * @param delay the delay to wait between the end of a poll and start of next one in milliseconds.
     */
    fun pollDelay(delay: Long)

    /**
     * Configures the monitoring of the poll step.
     */
    fun monitoring(monitoringConfig: StepMonitoringConfiguration.() -> Unit)
    fun flatten(): StepSpecification<Unit, RedisRecord<V>, *>
}

/**
 * Implementation of [LettucePollStepSpecification].
 *
 * @author Gabriel Moraes
 */
@Spec
internal class LettucePollStepSpecificationImpl<V : Any>(redisLettuceMethod: RedisLettuceScanMethod) :
    AbstractStepSpecification<Unit, LettucePollResult<V>, LettucePollStepSpecification<V>>(),
    LettucePollStepSpecification<V> {

    override val singletonConfiguration: SingletonConfiguration = SingletonConfiguration(SingletonType.UNICAST)

    internal var connection = RedisConnectionConfiguration()

    internal var keyOrPattern = ""

    internal var redisMethod = redisLettuceMethod

    @field:PositiveOrZeroDuration
    internal var pollDelay: Duration? = Duration.ofSeconds(10)

    internal var flattenOutput = false

    internal var monitoringConfig = StepMonitoringConfiguration()

    override fun connection(configBlock: RedisConnectionConfiguration.() -> Unit) {
        connection.configBlock()
    }

    override fun keyOrPattern(@NotBlank keyOrPattern: String) {
        this.keyOrPattern = keyOrPattern
    }

    override fun pollDelay(delay: Duration) {
        this.pollDelay = delay
    }

    override fun pollDelay(delay: Long) {
        this.pollDelay = Duration.of(delay, ChronoUnit.MILLIS)
    }

    override fun monitoring(monitoringConfig: StepMonitoringConfiguration.() -> Unit) {
        this.monitoringConfig.monitoringConfig()
    }

    override fun unicast(bufferSize: Int, idleTimeout: Duration) {
        singletonConfiguration.bufferSize = bufferSize
        singletonConfiguration.idleTimeout = idleTimeout
    }

    override fun flatten(): StepSpecification<Unit, RedisRecord<V>, *> {
        flattenOutput = true

        @Suppress("UNCHECKED_CAST")
        return this as StepSpecification<Unit, RedisRecord<V>, *>
    }
}

/**
 * Supported redis scan methods. For more details [see here](https://redis.io/commands/scan].
 *
 * @author Gabriel Moraes
 */
enum class RedisLettuceScanMethod {
    SCAN, SSCAN, HSCAN, ZSCAN
}

/**
 * Creates a Redis-Lettuce scan poll step in order to periodically fetch data from a Redis database using the scan command.
 * For more information about scan, [see here](https://redis.io/commands/scan).
 *
 * This step is generally used in conjunction with a join to assert data or inject them in a workflow.
 *
 * You can learn more on [lettuce website](https://lettuce.io/docs/getting-started.html).
 *
 * @author Gabriel Moraes
 */
fun RedisLettuceScenarioSpecification.pollScan(
    configurationBlock: LettucePollStepSpecification<String>.() -> Unit
): LettucePollStepSpecification<String> {
    val step = LettucePollStepSpecificationImpl<String>(RedisLettuceScanMethod.SCAN)
    step.configurationBlock()

    (this as StepSpecificationRegistry).add(step)
    return step
}

/**
 * Creates a Redis-Lettuce sscan poll step in order to periodically fetch data from a Redis database using the sscan command.
 * For more information about sscan, [see here](https://redis.io/commands/sscan).
 *
 * This step is generally used in conjunction with a join to assert data or inject them in a workflow.
 *
 * You can learn more on [lettuce website](https://lettuce.io/docs/getting-started.html).
 *
 * @author Gabriel Moraes
 */
fun RedisLettuceScenarioSpecification.pollSscan(
    configurationBlock: LettucePollStepSpecification<String>.() -> Unit
): LettucePollStepSpecification<String> {
    val step = LettucePollStepSpecificationImpl<String>(RedisLettuceScanMethod.SSCAN)
    step.configurationBlock()

    (this as StepSpecificationRegistry).add(step)
    return step
}

/**
 * Creates a Redis-Lettuce zscan poll step in order to periodically fetch data from a Redis database using the zscan command.
 * For more information about zscan, [see here](https://redis.io/commands/zscan).
 *
 * This step is generally used in conjunction with a join to assert data or inject them in a workflow.
 *
 * You can learn more on [lettuce website](https://lettuce.io/docs/getting-started.html).
 *
 * @author Gabriel Moraes
 */
fun RedisLettuceScenarioSpecification.pollZscan(
    configurationBlock: LettucePollStepSpecification<Pair<Double, String>>.() -> Unit
): LettucePollStepSpecification<Pair<Double, String>> {
    val step = LettucePollStepSpecificationImpl<Pair<Double, String>>(RedisLettuceScanMethod.ZSCAN)
    step.configurationBlock()

    (this as StepSpecificationRegistry).add(step)
    return step
}

/**
 * Creates a Redis-Lettuce hscan poll step in order to periodically fetch data from a Redis database using the hscan command.
 * For more information about hscan, [see here](https://redis.io/commands/hscan).
 *
 * This step is generally used in conjunction with a join to assert data or inject them in a workflow.
 *
 * You can learn more on [lettuce website](https://lettuce.io/docs/getting-started.html).
 *
 * @author Gabriel Moraes
 */
fun RedisLettuceScenarioSpecification.pollHscan(
    configurationBlock: LettucePollStepSpecification<Pair<String, String>>.() -> Unit
): LettucePollStepSpecification<Pair<String, String>> {
    val step = LettucePollStepSpecificationImpl<Pair<String, String>>(RedisLettuceScanMethod.HSCAN)
    step.configurationBlock()

    (this as StepSpecificationRegistry).add(step)
    return step
}
