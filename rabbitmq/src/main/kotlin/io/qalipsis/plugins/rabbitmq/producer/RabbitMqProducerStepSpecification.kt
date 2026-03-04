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
import io.qalipsis.plugins.rabbitmq.configuration.findRabbitMqDefaults
import javax.validation.constraints.Min
import kotlinx.coroutines.ExperimentalCoroutinesApi

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
        this.monitoring.monitoringConfiguration()
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
    findRabbitMqDefaults(this as StepSpecification<*, *, *>)?.applyTo(step)
    step.configurationBlock()

    this.add(step)
    return step
}
