package io.qalipsis.plugins.jms.consumer

import assertk.all
import assertk.assertThat
import assertk.assertions.*
import io.aerisconsulting.catadioptre.getProperty
import io.qalipsis.api.scenario.StepSpecificationRegistry
import io.qalipsis.api.scenario.scenario
import io.qalipsis.api.steps.SingletonConfiguration
import io.qalipsis.api.steps.SingletonType
import io.qalipsis.plugins.jms.jms
import org.apache.activemq.ActiveMQConnectionFactory
import org.junit.jupiter.api.Test
import java.time.Duration

/**
 *
 * @author Alexander Sosnovsky
 */
internal class JmsConsumerStepSpecificationTest {

    private val queueConnection = ActiveMQConnectionFactory().createQueueConnection()

    private val topicConnection = ActiveMQConnectionFactory().createTopicConnection()

    @Test
    internal fun `should apply queue connection`() {
        val scenario = scenario("my-scenario") as StepSpecificationRegistry
        scenario.jms().consume {
            queueConnection { queueConnection }
            queues("queue-1", "queue-2")
        }

        assertThat(scenario.rootSteps[0]).isInstanceOf(JmsConsumerStepSpecification::class).all {
            prop(JmsConsumerStepSpecification<*>::configuration).all {
                prop(JmsConsumerConfiguration::queues).hasSize(2)
                prop(JmsConsumerConfiguration::topics).hasSize(0)
                prop(JmsConsumerConfiguration::topicConnectionFactory).isNull()
            }
            transform { it.metrics }.all {
                prop(JmsConsumerMetricsConfiguration::bytesCount).isFalse()
                prop(JmsConsumerMetricsConfiguration::recordsCount).isFalse()
            }
            transform { it.singletonConfiguration }.all {
                prop(SingletonConfiguration::type).isEqualTo(SingletonType.UNICAST)
                prop(SingletonConfiguration::bufferSize).isEqualTo(-1)
                prop(SingletonConfiguration::idleTimeout).isEqualTo(Duration.ZERO)
            }
        }

        val step = scenario.rootSteps[0] as JmsConsumerStepSpecification<*>
        val queueConnectionFactory = step.configuration.getProperty<() -> String>("queueConnectionFactory")
        assertThat(queueConnectionFactory.invoke()).isEqualTo(queueConnection)
    }

    @Test
    internal fun `should apply topic connection`() {
        val scenario = scenario("my-scenario") as StepSpecificationRegistry
        scenario.jms().consume {
            topicConnection { topicConnection }
            topics("topic-1", "topic-2")
        }

        assertThat(scenario.rootSteps[0]).isInstanceOf(JmsConsumerStepSpecification::class).all {
            prop(JmsConsumerStepSpecification<*>::configuration).all {
                prop(JmsConsumerConfiguration::queues).hasSize(0)
                prop(JmsConsumerConfiguration::topics).hasSize(2)
                prop(JmsConsumerConfiguration::queueConnectionFactory).isNull()
            }
            transform { it.metrics }.all {
                prop(JmsConsumerMetricsConfiguration::bytesCount).isFalse()
                prop(JmsConsumerMetricsConfiguration::recordsCount).isFalse()
            }
            transform { it.singletonConfiguration }.all {
                prop(SingletonConfiguration::type).isEqualTo(SingletonType.UNICAST)
                prop(SingletonConfiguration::bufferSize).isEqualTo(-1)
                prop(SingletonConfiguration::idleTimeout).isEqualTo(Duration.ZERO)
            }
        }

        val step = scenario.rootSteps[0] as JmsConsumerStepSpecification<*>

        val topicConnectionFactory = step.configuration.getProperty<() -> String>("topicConnectionFactory")
        assertThat(topicConnectionFactory.invoke()).isEqualTo(topicConnection)
    }

    @Test
    internal fun `should apply bytes count`() {
        val scenario = scenario("my-scenario") as StepSpecificationRegistry
        scenario.jms().consume {
            queueConnection { queueConnection }
            queues("queue-1", "queue-2")

            metrics {
                bytesCount = true
            }
        }

        assertThat(scenario.rootSteps[0]).isInstanceOf(JmsConsumerStepSpecification::class).all {
            transform { it.metrics }.all {
                prop(JmsConsumerMetricsConfiguration::bytesCount).isTrue()
                prop(JmsConsumerMetricsConfiguration::recordsCount).isFalse()
            }
        }
    }

    @Test
    internal fun `should apply records count`() {
        val scenario = scenario("my-scenario") as StepSpecificationRegistry
        scenario.jms().consume {
            queueConnection { queueConnection }
            queues("queue-1", "queue-2")

            metrics {
                recordsCount = true
            }
        }

        assertThat(scenario.rootSteps[0]).isInstanceOf(JmsConsumerStepSpecification::class).all {
            transform { it.metrics }.all {
                prop(JmsConsumerMetricsConfiguration::bytesCount).isFalse()
                prop(JmsConsumerMetricsConfiguration::recordsCount).isTrue()
            }
        }
    }

}
