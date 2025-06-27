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

package io.qalipsis.plugins.jms.consumer

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
import io.qalipsis.plugins.jms.JmsDeserializer
import io.qalipsis.plugins.jms.JmsScenarioSpecification
import io.qalipsis.plugins.jms.deserializer.JmsStringDeserializer
import java.time.Duration
import javax.jms.QueueConnection
import javax.jms.TopicConnection
import javax.validation.constraints.NotBlank
import kotlin.reflect.KClass

interface JmsConsumerSpecification<O> : UnicastSpecification,
    ConfigurableStepSpecification<Unit, JmsConsumerResult<O>, JmsConsumerSpecification<O>>,
    SingletonStepSpecification {

    /**
     * Configures queueConnection to the JMS.
     */
    fun queueConnection(queueConnectionFactory: () -> QueueConnection)

    /**
     * Configures topicConnection to the JMS.
     */
    fun topicConnection(topicConnectionFactory: () -> TopicConnection)

    /**
     * Defines the list of topics to consume.
     */
    fun topics(vararg topics: String)

    /**
     * Defines the list of queues to consume.
     */
    fun queues(vararg queues: String)

    /**
     * Configuration of the metrics to apply, default to none.
     */
    fun metrics(metricsConfiguration: JmsConsumerMetricsConfiguration.() -> Unit)

    /**
     * Uses an instance of [valueDeserializer] to deserialize the message of JMS.
     * This class must be an implementation of [JmsDeserializer].
     */
    fun <V1 : Any> deserialize(valueDeserializer: String): StepSpecification<Unit, JmsConsumerResult<V1>, *>

    /**
     * Uses an instance of [valueDeserializer] to deserialize the message of JMS.
     * This class must be an implementation of [JmsDeserializer].
     */
    fun <V1 : Any> deserialize(
        valueDeserializer: KClass<out JmsDeserializer<V1>>
    ): StepSpecification<Unit, JmsConsumerResult<V1>, *>

    /**
     * Uses an instance of [valueDeserializer] to deserialize the message of JMS.
     * This class must be an implementation of [JmsDeserializer].
     */
    fun <V1 : Any> deserialize(
        valueDeserializer: JmsDeserializer<V1>
    ): StepSpecification<Unit, JmsConsumerResult<V1>, *>

    /**
     * Configures the monitoring of the consume step.
     */
    fun monitoring(monitoringConfig: StepMonitoringConfiguration.() -> Unit)
}

@Spec
internal class JmsConsumerStepSpecification<O : Any> internal constructor(
    deserializer: JmsDeserializer<O>
) : AbstractStepSpecification<Unit, JmsConsumerResult<O>, JmsConsumerSpecification<O>>(),
    JmsConsumerSpecification<O> {

    internal var valueDeserializer = deserializer

    internal val configuration = JmsConsumerConfiguration()

    internal val metrics = JmsConsumerMetricsConfiguration()

    internal val monitoringConfig = StepMonitoringConfiguration()

    override val singletonConfiguration: SingletonConfiguration = SingletonConfiguration(SingletonType.UNICAST)

    override fun topicConnection(topicConnectionFactory: () -> TopicConnection) {
        configuration.topicConnectionFactory = topicConnectionFactory
        configuration.queueConnectionFactory = null
    }

    override fun queueConnection(queueConnectionFactory: () -> QueueConnection) {
        configuration.queueConnectionFactory = queueConnectionFactory
        configuration.topicConnectionFactory = null
    }

    override fun topics(vararg topics: String) {
        configuration.topics.clear()
        configuration.topics.addAll(topics.toList())
        configuration.queues.clear()
    }

    override fun queues(vararg queues: String) {
        configuration.queues.clear()
        configuration.queues.addAll(queues.toList())
        configuration.topics.clear()
    }

    override fun monitoring(monitoringConfig: StepMonitoringConfiguration.() -> Unit) {
        this.monitoringConfig.monitoringConfig()
    }

    override fun metrics(
        metricsConfiguration: JmsConsumerMetricsConfiguration.() -> Unit
    ) {
        metrics.metricsConfiguration()
    }

    override fun unicast(bufferSize: Int, idleTimeout: Duration) {
        singletonConfiguration.bufferSize = bufferSize
        singletonConfiguration.idleTimeout = idleTimeout
    }

    @Suppress("UNCHECKED_CAST")
    override fun <V1 : Any> deserialize(valueDeserializer: String): StepSpecification<Unit, JmsConsumerResult<V1>, *> {
        this as JmsConsumerStepSpecification<V1>
        this.valueDeserializer =
            (Class.forName(valueDeserializer) as Class<JmsDeserializer<V1>>).getDeclaredConstructor().newInstance()

        return this
    }

    @Suppress("UNCHECKED_CAST")
    override fun <V1 : Any> deserialize(valueDeserializer: KClass<out JmsDeserializer<V1>>): StepSpecification<Unit, JmsConsumerResult<V1>, *> {

        this as JmsConsumerStepSpecification<V1>
        this.valueDeserializer = valueDeserializer.java.getDeclaredConstructor().newInstance()

        return this
    }

    @Suppress("UNCHECKED_CAST")
    override fun <V1 : Any> deserialize(valueDeserializer: JmsDeserializer<V1>): StepSpecification<Unit, JmsConsumerResult<V1>, *> {
        this as JmsConsumerStepSpecification<V1>
        this.valueDeserializer = valueDeserializer

        return this
    }

}

@Spec
internal data class JmsConsumerConfiguration(
    internal var topicConnectionFactory: (() -> TopicConnection)? = null,
    internal var queueConnectionFactory: (() -> QueueConnection)? = null,
    internal var topics: MutableList<@NotBlank String> = mutableListOf(),
    internal var queues: MutableList<@NotBlank String> = mutableListOf(),
    internal var properties: MutableMap<@NotBlank String, Any> = mutableMapOf()
)

/**
 * Configuration of the metrics to record for the JMS consumer.
 *
 * @property bytesCount when true, records the number of bytes consumed for the serialized keys.
 * @property recordsCount when true, records the number of consumed messages.
 *
 * @author Alexander Sosnovsky
 */
@Spec
data class JmsConsumerMetricsConfiguration(
    var bytesCount: Boolean = false,
    var recordsCount: Boolean = false,
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
data class JmsConsumerMonitoringConfiguration(
    var events: Boolean = false,
    var meters: Boolean = false,
)

/**
 * Creates a JMS consumers to poll data from topics or queues of JMS cluster and forward each message to the next step individually.
 *
 * This step is generally used in conjunction with a left join to assert data or inject them in a workflow.
 *
 * @author Alexander Sosnovsky
 */
fun JmsScenarioSpecification.consume(
    configurationBlock: JmsConsumerSpecification<String>.() -> Unit
): JmsConsumerSpecification<String> {
    val step = JmsConsumerStepSpecification(JmsStringDeserializer())
    step.configurationBlock()
    (this as StepSpecificationRegistry).add(step)
    return step
}
