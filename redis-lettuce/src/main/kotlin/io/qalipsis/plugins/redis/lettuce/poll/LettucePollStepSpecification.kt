package io.qalipsis.plugins.redis.lettuce.poll

import io.qalipsis.api.annotations.Spec
import io.qalipsis.api.constraints.PositiveOrZeroDuration
import io.qalipsis.api.scenario.StepSpecificationRegistry
import io.qalipsis.api.steps.AbstractStepSpecification
import io.qalipsis.api.steps.BroadcastSpecification
import io.qalipsis.api.steps.LoopableSpecification
import io.qalipsis.api.steps.SingletonConfiguration
import io.qalipsis.api.steps.SingletonStepSpecification
import io.qalipsis.api.steps.SingletonType
import io.qalipsis.api.steps.StepMonitoringConfiguration
import io.qalipsis.api.steps.StepSpecification
import io.qalipsis.api.steps.UnicastSpecification
import io.qalipsis.plugins.redis.lettuce.Flattenable
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
    StepSpecification<Unit, LettucePollResult<V>, Flattenable<RedisRecord<V>, LettucePollResult<V>>>,
    SingletonStepSpecification,
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
}

/**
 * Implementation of [LettucePollStepSpecification].
 *
 * @author Gabriel Moraes
 */
@Spec
internal class LettucePollStepSpecificationImpl<V : Any> internal constructor(
    redisLettuceMethod: RedisLettuceScanMethod
) :
    AbstractStepSpecification<Unit, LettucePollResult<V>, Flattenable<RedisRecord<V>, LettucePollResult<V>>>(),
    Flattenable<RedisRecord<V>, LettucePollResult<V>>, LettucePollStepSpecification<V> {

    override val singletonConfiguration: SingletonConfiguration = SingletonConfiguration(SingletonType.UNICAST)

    internal var connection = RedisConnectionConfiguration()

    internal var keyOrPattern = ""

    internal var redisMethod = redisLettuceMethod

    @field:PositiveOrZeroDuration
    internal var pollDelay: Duration? = Duration.ofSeconds(10)

    internal var monitoringConfig = StepMonitoringConfiguration()

    internal var flattenOutput = false

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

    override fun forwardOnce(bufferSize: Int, idleTimeout: Duration) {
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
): Flattenable<RedisRecord<String>, LettucePollResult<String>> {
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
): Flattenable<RedisRecord<String>, LettucePollResult<String>> {
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
): Flattenable<RedisRecord<Pair<Double, String>>, LettucePollResult<Pair<Double, String>>> {
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
): Flattenable<RedisRecord<Pair<String, String>>, LettucePollResult<Pair<String, String>>> {
    val step = LettucePollStepSpecificationImpl<Pair<String, String>>(RedisLettuceScanMethod.HSCAN)
    step.configurationBlock()

    (this as StepSpecificationRegistry).add(step)
    return step
}
