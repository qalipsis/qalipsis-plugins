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

package io.qalipsis.plugins.rabbitmq.producer

import com.rabbitmq.client.ConnectionFactory
import io.qalipsis.api.annotations.Spec
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.steps.AbstractStepSpecification
import io.qalipsis.api.steps.ConfigurableStepSpecification
import io.qalipsis.api.steps.StepMonitoringConfiguration
import io.qalipsis.api.steps.StepSpecification
import io.qalipsis.plugins.rabbitmq.RabbitMqStepSpecification
import io.qalipsis.plugins.rabbitmq.configuration.RabbitMqConnectionConfiguration
import kotlinx.coroutines.ExperimentalCoroutinesApi
import javax.validation.constraints.Min

/**
 * Specification for a [RabbitMqProducerStep] to produce messages to the RabbitMQ broker.
 *
 * @author Alexander Sosnovsky
 */
@ExperimentalCoroutinesApi
interface RabbitMqProducerStepSpecification<I> :
    StepSpecification<I, RabbitMqProducerResult<I>, RabbitMqProducerStepSpecification<I>>,
    RabbitMqStepSpecification<I, RabbitMqProducerResult<I>, RabbitMqProducerStepSpecification<I>>,
    ConfigurableStepSpecification<I, RabbitMqProducerResult<I>, RabbitMqProducerStepSpecification<I>> {

    /**
     * Configures the connection of the RabbitMQ broker, defaults to localhost:5672.
     */
    fun connection(configurationBlock: RabbitMqConnectionConfiguration.() -> Unit)

    /**
     * Closure to generate a list of [RabbitMqProducerRecord].
     */
    fun records(recordsFactory: suspend (ctx: StepContext<*, *>, input: Any) -> List<RabbitMqProducerRecord>)

    /**
     * Configures the monitoring of the step.
     */
    fun monitoring(monitoringConfiguration: StepMonitoringConfiguration.() -> Unit)

    /**
     * Defines the number of concurrent channels producing messages to RabbitMQ, defaults to 1.
     */
    fun concurrency(concurrency: Int)
}

/**
 * Implementation of [RabbitMqProducerStepSpecification].
 *
 * @author Alexander Sosnovsky
 */
@ExperimentalCoroutinesApi
@Spec
internal class RabbitMqProducerStepSpecificationImpl<I> :
    RabbitMqProducerStepSpecification<I>,
    AbstractStepSpecification<I, RabbitMqProducerResult<I>, RabbitMqProducerStepSpecification<I>>() {

    internal lateinit var connectionFactory: ConnectionFactory

    internal var recordsFactory: suspend (ctx: StepContext<*, *>, input: Any) -> List<RabbitMqProducerRecord> =
        { _, _ -> listOf() }

    internal var connectionConfiguration = RabbitMqConnectionConfiguration()

    internal var monitoring = StepMonitoringConfiguration()

    @field:Min(1)
    internal var concurrency: Int = 1

    override fun connection(configurationBlock: RabbitMqConnectionConfiguration.() -> Unit) {
        connectionConfiguration.configurationBlock()
    }

    override fun records(recordsFactory: suspend (ctx: StepContext<*, *>, input: Any) -> List<RabbitMqProducerRecord>) {
        this.recordsFactory = recordsFactory
    }

    override fun monitoring(monitoringConfiguration: StepMonitoringConfiguration.() -> Unit) {
        monitoringConfiguration.apply { monitoring }
    }

    override fun concurrency(concurrency: Int) {
        this.concurrency = concurrency
    }
}

/**
 * Provides messages to RabbitMQ broker using a io.qalipsis.plugins.rabbitmq.producer query.
 *
 * @author Alexander Sosnovsky
 */
@ExperimentalCoroutinesApi
fun <I> RabbitMqStepSpecification<*, I, *>.produce(
    configurationBlock: RabbitMqProducerStepSpecification<I>.() -> Unit
): RabbitMqProducerStepSpecification<I> {
    val step = RabbitMqProducerStepSpecificationImpl<I>()
    step.configurationBlock()

    this.add(step)
    return step
}
