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

package io.qalipsis.plugins.jakarta.producer

import io.qalipsis.api.annotations.Spec
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.steps.AbstractStepSpecification
import io.qalipsis.api.steps.ConfigurableStepSpecification
import io.qalipsis.api.steps.StepMonitoringConfiguration
import io.qalipsis.api.steps.StepSpecification
import io.qalipsis.plugins.jakarta.JakartaStepSpecification
import jakarta.jms.Connection
import kotlinx.coroutines.ExperimentalCoroutinesApi

/**
 * Specification for a [JakartaProducerStep] to produce native Jakarta [jakarta.jms.Message]s.
 *
 * @author Krawist Ngoben
 */
@ExperimentalCoroutinesApi
interface JakartaProducerStepSpecification<I> :
    StepSpecification<I, JakartaProducerResult<I>, JakartaProducerStepSpecification<I>>,
    ConfigurableStepSpecification<I, JakartaProducerResult<I>, JakartaProducerStepSpecification<I>>,
    JakartaStepSpecification<I, JakartaProducerResult<I>, JakartaProducerStepSpecification<I>> {

    /**
     * Configures the connection to the Jakarta server.
     */
    fun connect(connectionFactory: () -> Connection)

    /**
     * records closure to generate a list of [JakartaProducerRecord]
     */
    fun records(recordsFactory: suspend (ctx: StepContext<*, *>, input: I) -> List<JakartaProducerRecord>)

    /**
     * Configures the metrics of the step.
     */
    fun metrics(metricsConfiguration: JakartaProducerMetricsConfiguration.() -> Unit)

    /**
     * Configures the monitoring of the produce step.
     */
    fun monitoring(monitoringConfig: StepMonitoringConfiguration.() -> Unit)
}

/**
 * Implementation of [JakartaProducerStepSpecification].
 *
 * @author Alexander Sosnovsky
 */
@ExperimentalCoroutinesApi
@Spec
internal class JakartaProducerStepSpecificationImpl<I> :
    JakartaProducerStepSpecification<I>,
    AbstractStepSpecification<I, JakartaProducerResult<I>, JakartaProducerStepSpecification<I>>() {

    internal lateinit var connectionFactory: () -> Connection

    internal var recordsFactory: suspend (ctx: StepContext<*, *>, input: I) -> List<JakartaProducerRecord> =
        { _, _ -> listOf() }

    internal val metrics = JakartaProducerMetricsConfiguration()
    internal var monitoringConfig = StepMonitoringConfiguration()

    override fun connect(connectionFactory: () -> Connection) {
        this.connectionFactory = connectionFactory
    }

    override fun records(recordsFactory: suspend (ctx: StepContext<*, *>, input: I) -> List<JakartaProducerRecord>) {
        this.recordsFactory = recordsFactory
    }

    override fun metrics(metricsConfiguration: JakartaProducerMetricsConfiguration.() -> Unit) {
        metrics.metricsConfiguration()
    }

    override fun monitoring(monitoringConfig: StepMonitoringConfiguration.() -> Unit) {
        this.monitoringConfig.monitoringConfig()
    }

}

/**
 * Configuration of the metrics to record for the Jakarta producer.
 *
 * @property bytesCount when true, records the number of bytes produced messages.
 * @property recordsCount when true, records the number of produced messages.
 *
 * @author Alexander Sosnovsky
 */
@Spec
data class JakartaProducerMetricsConfiguration(
    var bytesCount: Boolean = false,
    var recordsCount: Boolean = false
)

/**
 * Configuration of the monitoring to record for the Jakarta producer step.
 *
 * @property events when true, records the events.
 * @property meters when true, records metrics.
 *
 * @author Alex Averianov
 */
@Spec
data class JakartaProducerMonitoringConfiguration(
    var events: Boolean = false,
    var meters: Boolean = false,
)

/**
 * Provides [jakarta.jms.Message] to JMS server using a io.qalipsis.plugins.jakarta.producer query.
 *
 * @author Alexander Sosnovsky
 */
@ExperimentalCoroutinesApi
fun <I> JakartaStepSpecification<*, I, *>.produce(
    configurationBlock: JakartaProducerStepSpecification<I>.() -> Unit
): JakartaProducerStepSpecification<I> {
    val step = JakartaProducerStepSpecificationImpl<I>()
    step.configurationBlock()

    this.add(step)
    return step
}
