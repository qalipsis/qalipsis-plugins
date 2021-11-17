package io.qalipsis.plugins.redis.lettuce.save

import io.qalipsis.api.annotations.Spec
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.steps.AbstractStepSpecification
import io.qalipsis.api.steps.StepMonitoringConfiguration
import io.qalipsis.api.steps.StepSpecification
import io.qalipsis.plugins.redis.lettuce.RedisLettuceStepSpecification
import io.qalipsis.plugins.redis.lettuce.configuration.RedisConnectionConfiguration
import io.qalipsis.plugins.redis.lettuce.configuration.RedisConnectionType.CLUSTER
import io.qalipsis.plugins.redis.lettuce.configuration.RedisConnectionType.SENTINEL
import io.qalipsis.plugins.redis.lettuce.configuration.RedisConnectionType.SINGLE

/**
 * Specification for a [LettuceSaveStep] to save data onto a Redis database.
 *
 * The output is a [LettuceSaveResult] that contains the output from previous step and metrics regarding this step.
 *
 * @author Gabriel Moraes
 */
@Spec
interface LettuceSaveStepSpecification<I> : StepSpecification<I, LettuceSaveResult<I>, LettuceSaveStepSpecification<I>> {

    /**
     * Configures the connection to the database.
     *
     * The connection type is specified in the [configBlock] by using either [SINGLE], [CLUSTER] or [SENTINEL].
     * It is also possible to connect to more than one node, this can be achieved by passing a more than one
     * parameter in the [List] of nodes for the [configBlock].
     *
     * For the [SENTINEL] connection type is required to pass a value for the masterId parameter.
     */
    fun connection(configurationBlock: RedisConnectionConfiguration.() -> Unit)

    /**
     * Defines the records to be saved, it receives the context and the output from previous step that can be used
     * when defining the records.
     */
    fun records(recordsConfiguration: suspend (stepContext: StepContext<*, *>, input: I) -> List<LettuceSaveRecord<*>>)

    /**
     * Configures the monitoring of the poll step.
     */
    fun monitoring(monitoringConfig: StepMonitoringConfiguration.() -> Unit)
}

/**
 * Implementation of [LettuceSaveStepSpecification].
 *
 * @author Gabriel Moraes
 */
@Spec
internal class LettuceSaveStepSpecificationImpl<I> : AbstractStepSpecification<I, LettuceSaveResult<I>, LettuceSaveStepSpecification<I>>(), LettuceSaveStepSpecification<I> {

    internal var connectionConfiguration = RedisConnectionConfiguration()

    internal var monitoringConfig = StepMonitoringConfiguration()

    internal var recordsFactory: (suspend (stepContext: StepContext<*, *>, input: I) -> List<LettuceSaveRecord<*>>) =
        { _, _ -> emptyList() }

    override fun connection(configurationBlock: RedisConnectionConfiguration.() -> Unit) {
        connectionConfiguration.configurationBlock()
    }

    override fun records(recordsConfiguration: suspend (stepContext: StepContext<*, *>, input: I) -> List<LettuceSaveRecord<*>>) {
        recordsFactory = recordsConfiguration
    }

    override fun monitoring(monitoringConfig: StepMonitoringConfiguration.() -> Unit) {
        this.monitoringConfig.monitoringConfig()
    }

}

/**
 * Supported redis methods to save records.
 *
 * @author Gabriel Moraes
 */
enum class RedisLettuceSaveMethod {
    SET, SADD, HSET, ZADD
}

/**
 * Creates a step to save data onto a Redis database and forwards the input to the next step.
 *
 * You can learn more on [lettuce website](https://lettuce.io/docs/getting-started.html).
 *
 * @author Gabriel Moraes
 */
fun <I> RedisLettuceStepSpecification<*, I, *>.save(
    configurationBlock: LettuceSaveStepSpecification<I>.() -> Unit
): LettuceSaveStepSpecification<I> {
    val step = LettuceSaveStepSpecificationImpl<I>()
    step.configurationBlock()

    this.add(step)
    return step
}



