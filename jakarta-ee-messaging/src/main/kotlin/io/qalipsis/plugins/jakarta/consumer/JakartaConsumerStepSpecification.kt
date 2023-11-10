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

package io.qalipsis.plugins.jakarta.consumer

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
import io.qalipsis.plugins.jakarta.JakartaDeserializer
import io.qalipsis.plugins.jakarta.JakartaScenarioSpecification
import io.qalipsis.plugins.jakarta.deserializer.JakartaStringDeserializer
import jakarta.jms.Connection
import jakarta.jms.QueueConnection
import jakarta.jms.Session
import jakarta.jms.TopicConnection
import java.time.Duration
import javax.validation.constraints.NotBlank
import kotlin.reflect.KClass

interface JakartaConsumerSpecification<O> : UnicastSpecification,
    ConfigurableStepSpecification<Unit, JakartaConsumerResult<O>, JakartaConsumerSpecification<O>>,
    SingletonStepSpecification {

    /**
     * Configures queueConnection to the Jakarta.
     */
    fun queueConnection(queueConnectionFactory: () -> QueueConnection)

    /**
     * Configures topicConnection to the Jakarta.
     */
    fun topicConnection(topicConnectionFactory: () -> TopicConnection)

    /**
     * Configures the session to the Jakarta server.
     */
    fun session(sessionFactory: (connection: Connection) -> Session)

    /**
     * Defines the list of topics to consume.
     */
    fun topics(vararg topics: String)

    /**
     * Defines the list of queues to consume.
     */
    fun queues(vararg queues: String)

    /**
     * Uses an instance of [valueDeserializer] to deserialize the message of Jakarta.
     * This class must be an implementation of [JakartaDeserializer].
     */
    fun <V1 : Any> deserialize(valueDeserializer: String): StepSpecification<Unit, JakartaConsumerResult<V1>, *>

    /**
     * Uses an instance of [valueDeserializer] to deserialize the message of Jakarta.
     * This class must be an implementation of [JakartaDeserializer].
     */
    fun <V1 : Any> deserialize(
        valueDeserializer: KClass<out JakartaDeserializer<V1>>
    ): StepSpecification<Unit, JakartaConsumerResult<V1>, *>

    /**
     * Uses an instance of [valueDeserializer] to deserialize the message of Jakarta.
     * This class must be an implementation of [JakartaDeserializer].
     */
    fun <V1 : Any> deserialize(
        valueDeserializer: JakartaDeserializer<V1>
    ): StepSpecification<Unit, JakartaConsumerResult<V1>, *>

    /**
     * Configures the monitoring of the consume step.
     */
    fun monitoring(monitoringConfig: StepMonitoringConfiguration.() -> Unit)
}

@Spec
internal class JakartaConsumerStepSpecification<O : Any> internal constructor(
    deserializer: JakartaDeserializer<O>
) : AbstractStepSpecification<Unit, JakartaConsumerResult<O>, JakartaConsumerSpecification<O>>(),
    JakartaConsumerSpecification<O> {

    internal var valueDeserializer = deserializer

    internal val configuration = JakartaConsumerConfiguration()

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

    override fun session(sessionFactory: (connection: Connection) -> Session) {
        configuration.sessionFactory = sessionFactory
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

    override fun unicast(bufferSize: Int, idleTimeout: Duration) {
        singletonConfiguration.bufferSize = bufferSize
        singletonConfiguration.idleTimeout = idleTimeout
    }

    @Suppress("UNCHECKED_CAST")
    override fun <V1 : Any> deserialize(valueDeserializer: String): StepSpecification<Unit, JakartaConsumerResult<V1>, *> {
        this as JakartaConsumerStepSpecification<V1>
        this.valueDeserializer =
            (Class.forName(valueDeserializer) as Class<JakartaDeserializer<V1>>).getDeclaredConstructor().newInstance()

        return this
    }

    @Suppress("UNCHECKED_CAST")
    override fun <V1 : Any> deserialize(valueDeserializer: KClass<out JakartaDeserializer<V1>>): StepSpecification<Unit, JakartaConsumerResult<V1>, *> {

        this as JakartaConsumerStepSpecification<V1>
        this.valueDeserializer = valueDeserializer.java.getDeclaredConstructor().newInstance()

        return this
    }

    @Suppress("UNCHECKED_CAST")
    override fun <V1 : Any> deserialize(valueDeserializer: JakartaDeserializer<V1>): StepSpecification<Unit, JakartaConsumerResult<V1>, *> {
        this as JakartaConsumerStepSpecification<V1>
        this.valueDeserializer = valueDeserializer

        return this
    }

}

@Spec
internal class JakartaConsumerConfiguration {

    internal var sessionFactory: (connection: Connection) -> Session = { connection -> connection.createSession() }

    internal var topicConnectionFactory: (() -> TopicConnection)? = null

    internal var queueConnectionFactory: (() -> QueueConnection)? = null

    internal var topics: MutableList<@NotBlank String> = mutableListOf()

    internal var queues: MutableList<@NotBlank String> = mutableListOf()

    internal var properties: MutableMap<@NotBlank String, Any> = mutableMapOf()

}

/**
 * Creates a Jakarta consumers to poll data from topics or queues of Jakarta cluster and forward each message to the next step individually.
 *
 * This step is generally used in conjunction with a left join to assert data or inject them in a workflow.
 *
 * @author Krawist Ngoben
 */
fun JakartaScenarioSpecification.consume(
    configurationBlock: JakartaConsumerSpecification<String>.() -> Unit
): JakartaConsumerSpecification<String> {
    val step = JakartaConsumerStepSpecification(JakartaStringDeserializer())
    step.configurationBlock()
    (this as StepSpecificationRegistry).add(step)
    return step
}
