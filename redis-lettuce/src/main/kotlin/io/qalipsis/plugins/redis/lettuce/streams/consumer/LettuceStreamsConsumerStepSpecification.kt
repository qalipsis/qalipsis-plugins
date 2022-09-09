package io.qalipsis.plugins.redis.lettuce.streams.consumer

import io.qalipsis.api.annotations.Spec
import io.qalipsis.api.scenario.StepSpecificationRegistry
import io.qalipsis.api.steps.AbstractStepSpecification
import io.qalipsis.api.steps.ConfigurableStepSpecification
import io.qalipsis.api.steps.SingletonConfiguration
import io.qalipsis.api.steps.SingletonStepSpecification
import io.qalipsis.api.steps.SingletonType
import io.qalipsis.api.steps.StepMonitoringConfiguration
import io.qalipsis.api.steps.StepSpecification
import io.qalipsis.api.steps.UnicastSpecification
import io.qalipsis.plugins.redis.lettuce.RedisLettuceScenarioSpecification
import io.qalipsis.plugins.redis.lettuce.configuration.RedisConnectionConfiguration
import io.qalipsis.plugins.redis.lettuce.configuration.RedisConnectionType.CLUSTER
import io.qalipsis.plugins.redis.lettuce.configuration.RedisConnectionType.SENTINEL
import io.qalipsis.plugins.redis.lettuce.configuration.RedisConnectionType.SINGLE
import java.time.Duration
import javax.validation.constraints.NotBlank
import javax.validation.constraints.Positive

/**
 * Specification for an [io.qalipsis.api.steps.datasource.IterativeDatasourceStep] to consume data from a Redis streams.
 *
 * The output is a list of [LettuceStreamsConsumedRecord] containing the deserialized value.
 *
 * When [flatten] is called, the records are provided one by one to the next step.
 *
 * @author Gabriel Moraes
 */
@Spec
interface LettuceStreamsConsumerStepSpecification : UnicastSpecification,
    StepSpecification<Unit, LettuceStreamsConsumerResult, LettuceStreamsConsumerStepSpecification>,
    ConfigurableStepSpecification<Unit, LettuceStreamsConsumerResult, LettuceStreamsConsumerStepSpecification> {

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
     * Defines the name of the streams to consume.
     */
    fun streamKey(key: String)

    /**
     * Consumer group name, no default.
     */
    fun group(name: String)

    /**
     * Defines the strategy to apply when the consumer group is not known from the cluster, defaults to
     * [LettuceStreamsConsumerOffset.FROM_BEGINNING].
     */
    fun offset(offsetConsumer: LettuceStreamsConsumerOffset)

    /**
     * Defines the number of concurrent consumers, defaults to 1.
     */
    fun concurrency(concurrency: Int)

    /**
     * Configures the monitoring of the poll step.
     */
    fun monitoring(monitoringConfig: StepMonitoringConfiguration.() -> Unit)

    /**
     * Returns the records individually.
     */
    fun flatten(): StepSpecification<Unit, LettuceStreamsConsumedRecord, *>
}

/**
 * Implementation of [LettuceStreamsConsumerStepSpecification].
 *
 * @author Gabriel Moraes
 */
@Spec
internal class LettuceStreamsConsumerStepSpecificationImpl :
    AbstractStepSpecification<Unit, LettuceStreamsConsumerResult, LettuceStreamsConsumerStepSpecification>(),
    LettuceStreamsConsumerStepSpecification, SingletonStepSpecification {

    override val singletonConfiguration: SingletonConfiguration = SingletonConfiguration(SingletonType.UNICAST)

    internal var connection = RedisConnectionConfiguration()

    internal var monitoringConfig = StepMonitoringConfiguration()

    @field:NotBlank
    internal var streamKey = ""

    @field:NotBlank
    internal var groupName: String = ""

    internal var offset: LettuceStreamsConsumerOffset = LettuceStreamsConsumerOffset.FROM_BEGINNING

    @field:Positive
    internal var concurrency: Int = 1

    internal var flattenOutput = false

    override fun connection(configBlock: RedisConnectionConfiguration.() -> Unit) {
        connection.configBlock()
    }

    override fun monitoring(monitoringConfig: StepMonitoringConfiguration.() -> Unit) {
        this.monitoringConfig.monitoringConfig()
    }

    override fun streamKey(key: String) {
        streamKey = key
    }

    override fun group(name: String) {
        groupName = name
    }

    override fun offset(offsetConsumer: LettuceStreamsConsumerOffset) {
        this.offset = offsetConsumer
    }

    override fun concurrency(concurrency: Int) {
        this.concurrency = concurrency
    }

    override fun unicast(bufferSize: Int, idleTimeout: Duration) {
        singletonConfiguration.bufferSize = bufferSize
        singletonConfiguration.idleTimeout = idleTimeout
    }

    override fun flatten(): StepSpecification<Unit, LettuceStreamsConsumedRecord, *> {
        flattenOutput = true

        @Suppress("UNCHECKED_CAST")
        return this as StepSpecification<Unit, LettuceStreamsConsumedRecord, *>
    }
}

/**
 * Supported redis streams offset for a consumer group.
 *
 * @author Gabriel Moraes
 */
@Spec
enum class LettuceStreamsConsumerOffset(internal val value: String) {
    FROM_BEGINNING("0-0"), LAST_CONSUMED(">"), LATEST("$")
}

/**
 * Creates a Lettuce redis streams consumers to received pushed data from streams of a Redis broker and forward each
 * message to the next step, either as batches or individually.
 *
 * This step is generally used in conjunction with join to assert data or inject them in a workflow.
 *
 * You can learn more on [lettuce website](https://lettuce.io/docs/getting-started.html).
 *
 * @author Gabriel Moraes
 */
fun RedisLettuceScenarioSpecification.streamsConsume(
    configurationBlock: LettuceStreamsConsumerStepSpecification.() -> Unit
): LettuceStreamsConsumerStepSpecification {
    val step = LettuceStreamsConsumerStepSpecificationImpl()
    step.configurationBlock()

    (this as StepSpecificationRegistry).add(step)
    return step
}