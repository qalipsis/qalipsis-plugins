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

package io.qalipsis.plugins.jms.producer

import io.qalipsis.api.annotations.Spec
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.steps.AbstractStepSpecification
import io.qalipsis.api.steps.ConfigurableStepSpecification
import io.qalipsis.api.steps.StepMonitoringConfiguration
import io.qalipsis.api.steps.StepSpecification
import io.qalipsis.plugins.jms.JmsStepSpecification
import kotlinx.coroutines.ExperimentalCoroutinesApi
import javax.jms.Connection

/**
 * Specification for a [JmsProducerStep] to produce native JMS [Message]s.
 *
 * @author Alexander Sosnovsky
 */
@ExperimentalCoroutinesApi
interface JmsProducerStepSpecification<I> :
    StepSpecification<I, JmsProducerResult<I>, JmsProducerStepSpecification<I>>,
    ConfigurableStepSpecification<I, JmsProducerResult<I>, JmsProducerStepSpecification<I>>,
    JmsStepSpecification<I, JmsProducerResult<I>, JmsProducerStepSpecification<I>> {

    /**
     * Configures the connection to the Jms server.
     */
    fun connect(connectionFactory: () -> Connection)

    /**
     * records closure to generate a list of [JmsProducerRecord]
     */
    fun records(recordsFactory: suspend (ctx: StepContext<*, *>, input: I) -> List<JmsProducerRecord>)

    /**
     * Configures the metrics of the step.
     */
    fun metrics(metricsConfiguration: JmsProducerMetricsConfiguration.() -> Unit)

    /**
     * Configures the monitoring of the produce step.
     */
    fun monitoring(monitoringConfig: StepMonitoringConfiguration.() -> Unit)
}

/**
 * Implementation of [JmsProducerStepSpecification].
 *
 * @author Alexander Sosnovsky
 */
@ExperimentalCoroutinesApi
@Spec
internal class JmsProducerStepSpecificationImpl<I> :
    JmsProducerStepSpecification<I>,
    AbstractStepSpecification<I, JmsProducerResult<I>, JmsProducerStepSpecification<I>>() {

    internal lateinit var connectionFactory: () -> Connection

    internal var recordsFactory: suspend (ctx: StepContext<*, *>, input: I) -> List<JmsProducerRecord> =
        { _, _ -> listOf() }

    internal val metrics = JmsProducerMetricsConfiguration()
    internal var monitoringConfig = StepMonitoringConfiguration()

    override fun connect(connectionFactory: () -> Connection) {
        this.connectionFactory = connectionFactory
    }

    override fun records(recordsFactory: suspend (ctx: StepContext<*, *>, input: I) -> List<JmsProducerRecord>) {
        this.recordsFactory = recordsFactory
    }

    override fun metrics(metricsConfiguration: JmsProducerMetricsConfiguration.() -> Unit) {
        metrics.metricsConfiguration()
    }

    override fun monitoring(monitoringConfig: StepMonitoringConfiguration.() -> Unit) {
        this.monitoringConfig.monitoringConfig()
    }

}

/**
 * Configuration of the metrics to record for the JMS producer.
 *
 * @property bytesCount when true, records the number of bytes produced messages.
 * @property recordsCount when true, records the number of produced messages.
 *
 * @author Alexander Sosnovsky
 */
@Spec
data class JmsProducerMetricsConfiguration(
    var bytesCount: Boolean = false,
    var recordsCount: Boolean = false
)

/**
 * Configuration of the monitoring to record for the JMS producer step.
 *
 * @property events when true, records the events.
 * @property meters when true, records metrics.
 *
 * @author Alex Averianov
 */
@Spec
data class JmsProducerMonitoringConfiguration(
    var events: Boolean = false,
    var meters: Boolean = false,
)

/**
 * Provides [Message] to JMS server using a io.qalipsis.plugins.jms.producer query.
 *
 * @author Alexander Sosnovsky
 */
@ExperimentalCoroutinesApi
fun <I> JmsStepSpecification<*, I, *>.produce(
    configurationBlock: JmsProducerStepSpecification<I>.() -> Unit
): JmsProducerStepSpecification<I> {
    val step = JmsProducerStepSpecificationImpl<I>()
    step.configurationBlock()

    this.add(step)
    return step
}
