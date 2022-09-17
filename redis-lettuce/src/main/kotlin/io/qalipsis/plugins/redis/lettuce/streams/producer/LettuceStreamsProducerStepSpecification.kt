/*
 * Copyright 2022 AERIS IT Solutions GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package io.qalipsis.plugins.redis.lettuce.streams.producer

import io.qalipsis.api.annotations.Spec
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.steps.AbstractStepSpecification
import io.qalipsis.api.steps.ConfigurableStepSpecification
import io.qalipsis.api.steps.StepMonitoringConfiguration
import io.qalipsis.api.steps.StepSpecification
import io.qalipsis.plugins.redis.lettuce.RedisLettuceStepSpecification
import io.qalipsis.plugins.redis.lettuce.configuration.RedisConnectionConfiguration
import io.qalipsis.plugins.redis.lettuce.configuration.RedisConnectionType.CLUSTER
import io.qalipsis.plugins.redis.lettuce.configuration.RedisConnectionType.SENTINEL
import io.qalipsis.plugins.redis.lettuce.configuration.RedisConnectionType.SINGLE

/**
 * Specification for a [LettuceStreamsProducerStep] to produce data onto a Redis stream.
 *
 * The output is a [LettuceStreamsProducerResult] that contains the output from previous step and metrics regarding this step.
 *
 * @author Gabriel Moraes
 */
@Spec
interface LettuceStreamsProducerStepSpecification<I> :
    StepSpecification<I, LettuceStreamsProducerResult<I>, LettuceStreamsProducerStepSpecification<I>>,
    ConfigurableStepSpecification<I, LettuceStreamsProducerResult<I>, LettuceStreamsProducerStepSpecification<I>> {

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
     * Defines the records to be published, it receives the context and the output from previous step that can be used
     * when defining the records.
     */
    fun records(recordsConfiguration: suspend (stepContext: StepContext<*, *>, input: I) -> List<LettuceStreamsProduceRecord>)

    /**
     * Configures the monitoring of the poll step.
     */
    fun monitoring(monitoringConfig: StepMonitoringConfiguration.() -> Unit)
}

/**
 * Specification to a Redis streams publisher, implementation of [LettuceStreamsProducerStepSpecification].
 *
 * @author Gabriel Moraes
 */
@Spec
internal class LettuceStreamsProducerStepSpecificationImpl<I> :
    AbstractStepSpecification<I, LettuceStreamsProducerResult<I>, LettuceStreamsProducerStepSpecification<I>>(),
    LettuceStreamsProducerStepSpecification<I> {

    internal var monitoringConfig = StepMonitoringConfiguration()

    internal var connection = RedisConnectionConfiguration()

    override fun connection(configBlock: RedisConnectionConfiguration.() -> Unit) {
        connection.configBlock()
    }

    override fun monitoring(monitoringConfig: StepMonitoringConfiguration.() -> Unit) {
        this.monitoringConfig.monitoringConfig()
    }

    internal var recordsFactory: (suspend (stepContext: StepContext<*, *>, input: I) ->
    List<LettuceStreamsProduceRecord>) = { _, _ -> emptyList() }

    override fun records(recordsConfiguration: suspend (stepContext: StepContext<*, *>, input: I) -> List<LettuceStreamsProduceRecord>) {
        this.recordsFactory = recordsConfiguration
    }

}


/**
 * Creates a step to push data onto streams of a Redis broker and forwards the input to the next step.
 *
 * You can learn more on [lettuce website](https://lettuce.io/docs/getting-started.html).
 *
 * @author Gabriel Moraes
 */
fun <I> RedisLettuceStepSpecification<*, I, *>.streamsProduce(
    configurationBlock: LettuceStreamsProducerStepSpecification<I>.() -> Unit
): LettuceStreamsProducerStepSpecification<I> {
    val step = LettuceStreamsProducerStepSpecificationImpl<I>()
    step.configurationBlock()

    this.add(step)
    return step
}