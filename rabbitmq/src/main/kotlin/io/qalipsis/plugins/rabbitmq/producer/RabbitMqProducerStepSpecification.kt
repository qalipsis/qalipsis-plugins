package io.qalipsis.plugins.rabbitmq.producer

import com.rabbitmq.client.ConnectionFactory
import io.qalipsis.api.annotations.Spec
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.steps.AbstractStepSpecification
import io.qalipsis.api.steps.StepSpecification
import io.qalipsis.plugins.rabbitmq.RabbitMqStepSpecification
import io.qalipsis.plugins.rabbitmq.configuration.RabbitMqConnectionConfiguration
import kotlinx.coroutines.ExperimentalCoroutinesApi
import javax.validation.constraints.Min

/**
 * Specification for a [io.qalipsis.plugins.mondodb.search.RabbitMqProducerStep] to produce messages to the RabbitMQ broker.
 *
 * @author Alexander Sosnovsky
 */
@ExperimentalCoroutinesApi
interface RabbitMqProducerStepSpecification<I> :
    StepSpecification<I, Pair<I, RabbitMqProducerRecord>, RabbitMqProducerStepSpecification<I>>,
    RabbitMqStepSpecification<I, Pair<I, RabbitMqProducerRecord>, RabbitMqProducerStepSpecification<I>> {

    /**
     * Configures the connection of the RabbitMQ broker, defaults to localhost:5672.
     */
    fun connection(configurationBlock: RabbitMqConnectionConfiguration.() -> Unit)

    /**
     * Closure to generate a list of [RabbitMqProducerRecord].
     */
    fun records(recordsFactory: suspend (ctx: StepContext<*, *>, input: Any) -> List<RabbitMqProducerRecord>)

    /**
     * Configures the metrics of the step.
     */
    fun metrics(metricsConfiguration: RabbitMqProducerMetricsConfiguration.() -> Unit)

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
    AbstractStepSpecification<I, Pair<I, RabbitMqProducerRecord>, RabbitMqProducerStepSpecification<I>>() {

    internal lateinit var connectionFactory: ConnectionFactory

    internal var recordsFactory: suspend (ctx: StepContext<*, *>, input: Any) -> List<RabbitMqProducerRecord> = { _, _ -> listOf() }

    internal var connectionConfiguration = RabbitMqConnectionConfiguration()

    internal val metrics = RabbitMqProducerMetricsConfiguration()

    @field:Min(1)
    internal var concurrency: Int = 1

    override fun connection(configurationBlock: RabbitMqConnectionConfiguration.() -> Unit) {
        connectionConfiguration.configurationBlock()
    }

    override fun records(recordsFactory: suspend (ctx: StepContext<*, *>, input: Any) -> List<RabbitMqProducerRecord>) {
        this.recordsFactory = recordsFactory
    }

    override fun metrics(metricsConfiguration: RabbitMqProducerMetricsConfiguration.() -> Unit) {
        metrics.metricsConfiguration()
    }

    override fun concurrency(concurrency: Int) {
        this.concurrency = concurrency
    }
}

/**
 * Configuration of the metrics to record for the RabbitMQ producer.
 *
 * @property bytesCount when true, records the number of bytes produced messages.
 * @property recordsCount when true, records the number of produced messages.
 *
 * @author Alexander Sosnovsky
 */
@Spec
data class RabbitMqProducerMetricsConfiguration(
    var bytesCount: Boolean = false,
    var recordsCount: Boolean = false,
)

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
