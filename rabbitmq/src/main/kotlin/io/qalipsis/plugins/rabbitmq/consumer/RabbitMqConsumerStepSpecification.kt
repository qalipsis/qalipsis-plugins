package io.qalipsis.plugins.rabbitmq.consumer

import io.qalipsis.api.annotations.Spec
import io.qalipsis.api.messaging.deserializer.MessageDeserializer
import io.qalipsis.api.messaging.deserializer.MessageStringDeserializer
import io.qalipsis.api.scenario.StepSpecificationRegistry
import io.qalipsis.api.steps.AbstractStepSpecification
import io.qalipsis.api.steps.ConfigurableStepSpecification
import io.qalipsis.api.steps.SingletonConfiguration
import io.qalipsis.api.steps.SingletonStepSpecification
import io.qalipsis.api.steps.SingletonType
import io.qalipsis.api.steps.StepMonitoringConfiguration
import io.qalipsis.api.steps.StepSpecification
import io.qalipsis.api.steps.UnicastSpecification
import io.qalipsis.plugins.rabbitmq.RabbitMqScenarioSpecification
import io.qalipsis.plugins.rabbitmq.RabbitMqStepSpecification
import io.qalipsis.plugins.rabbitmq.configuration.RabbitMqConnectionConfiguration
import java.time.Duration
import javax.validation.constraints.Min
import javax.validation.constraints.NotBlank
import javax.validation.constraints.PositiveOrZero
import kotlin.reflect.KClass

interface RabbitMqConsumerStepSpecification<V : Any> : UnicastSpecification,
    ConfigurableStepSpecification<Unit, RabbitMqConsumerRecord<V?>, RabbitMqDeserializerSpecification<V>> {

    /**
     * Configures the connection of the RabbitMQ broker, defaults to localhost:5672.
     */
    fun connection(configurationBlock: RabbitMqConnectionConfiguration.() -> Unit)

    /**
     * Defines the name of the queue to consume.
     */
    fun queue(queueName: String)

    /**
     * Defines the prefetch count value, defaults to 10.
     * For more information, see [here](https://www.rabbitmq.com/consumer-prefetch.html).
     */
    fun prefetchCount(prefetchValue: Int)

    /**
     * Defines the number of concurrent channels consuming the queue, default to 1.
     */
    fun concurrency(concurrency: Int)

    /**
     * Configures the monitoring to apply, default to none.
     */
    fun monitoring(monitoringConfig: StepMonitoringConfiguration.() -> Unit)
}

interface RabbitMqDeserializerSpecification<V : Any> :
    StepSpecification<Unit, RabbitMqConsumerRecord<V?>, RabbitMqDeserializerSpecification<V>> {

    /**
     * Uses an instance of [valueDeserializer] to deserialize the native values of RabbitMQ.
     * This class must be an implementation of [MessageDeserializer].
     */
    fun <V1 : Any> deserialize(valueDeserializer: String): StepSpecification<Unit, RabbitMqConsumerRecord<V1?>, *>

    /**
     * Uses an instance of [valueDeserializer] to deserialize the native values of RabbitMQ.
     * This class must be an implementation of [MessageDeserializer].
     */
    fun <V1 : Any> deserialize(
        valueDeserializer: KClass<out MessageDeserializer<V1>>
    ): StepSpecification<Unit, RabbitMqConsumerRecord<V1?>, *>

    /**
     * Uses an instance of [valueDeserializer] to deserialize the native values of RabbitMQ.
     * This class must be an implementation of [MessageDeserializer].
     */
    fun <V1 : Any> deserialize(
        valueDeserializer: MessageDeserializer<V1>
    ): StepSpecification<Unit, RabbitMqConsumerRecord<V1?>, *>
}

/**
 * Specification to a RabbitMQ consumer, implementation of [RabbitMqConsumerStepSpecification].
 *
 * @author Gabriel Moraes
 */
@Spec
internal class RabbitMqConsumerStepSpecificationImpl<V : Any> internal constructor(
    deserializer: MessageDeserializer<V>,
) : AbstractStepSpecification<Unit, RabbitMqConsumerRecord<V?>, RabbitMqDeserializerSpecification<V>>(),
    RabbitMqConsumerStepSpecification<V>, RabbitMqDeserializerSpecification<V>,
    RabbitMqStepSpecification<Unit, RabbitMqConsumerRecord<V?>, RabbitMqDeserializerSpecification<V>>,
    SingletonStepSpecification {

    internal var valueDeserializer = deserializer

    override val singletonConfiguration: SingletonConfiguration = SingletonConfiguration(SingletonType.UNICAST)

    internal var connectionConfiguration = RabbitMqConnectionConfiguration()

    internal var monitoringConfig = StepMonitoringConfiguration()

    @field:NotBlank
    internal var queueName = ""

    @field:PositiveOrZero
    internal var prefetchCount: Int = 10

    @field:Min(1)
    internal var concurrency: Int = 1

    override fun monitoring(monitoringConfig: StepMonitoringConfiguration.() -> Unit) {
        this.monitoringConfig.monitoringConfig()
    }

    override fun connection(configurationBlock: RabbitMqConnectionConfiguration.() -> Unit) {
        connectionConfiguration.configurationBlock()
    }

    override fun queue(queueName: String) {
        this.queueName = queueName
    }

    override fun prefetchCount(prefetchValue: Int) {
        prefetchCount = prefetchValue
    }

    override fun concurrency(concurrency: Int) {
        this.concurrency = concurrency
    }

    override fun forwardOnce(bufferSize: Int, idleTimeout: Duration) {
        singletonConfiguration.bufferSize = bufferSize
        singletonConfiguration.idleTimeout = idleTimeout
    }

    @Suppress("UNCHECKED_CAST")
    override fun <V1 : Any> deserialize(
        valueDeserializer: String
    ): StepSpecification<Unit, RabbitMqConsumerRecord<V1?>, *> {

        this as RabbitMqConsumerStepSpecificationImpl<V1>
        this.valueDeserializer =
            (Class.forName(valueDeserializer) as Class<MessageDeserializer<V1>>).getDeclaredConstructor().newInstance()

        return this
    }

    @Suppress("UNCHECKED_CAST")
    override fun <V1 : Any> deserialize(
        valueDeserializer: KClass<out MessageDeserializer<V1>>
    ): StepSpecification<Unit, RabbitMqConsumerRecord<V1?>, *> {

        this as RabbitMqConsumerStepSpecificationImpl<V1>
        this.valueDeserializer = valueDeserializer.java.getDeclaredConstructor().newInstance()

        return this
    }

    @Suppress("UNCHECKED_CAST")
    override fun <V1 : Any> deserialize(
        valueDeserializer: MessageDeserializer<V1>
    ): StepSpecification<Unit, RabbitMqConsumerRecord<V1?>, *> {

        this as RabbitMqConsumerStepSpecificationImpl<V1>
        this.valueDeserializer = valueDeserializer

        return this
    }

}

/**
 * Creates a RabbitMQ consumers to received pushed data from queues of a RabbitMQ broker and forward each message
 * to the next step.
 *
 * This step is generally used in conjunction with join to assert data or inject them in a workflow.
 *
 * You can learn more on [RabbitMQ website](https://www.rabbitmq.com/#getstarted).
 *
 * @author Gabriel Moraes
 */
fun RabbitMqScenarioSpecification.consume(
    configurationBlock: RabbitMqConsumerStepSpecification<String>.() -> Unit
): RabbitMqDeserializerSpecification<String> {
    val step = RabbitMqConsumerStepSpecificationImpl(MessageStringDeserializer())
    step.configurationBlock()
    (this as StepSpecificationRegistry).add(step)
    return step
}
