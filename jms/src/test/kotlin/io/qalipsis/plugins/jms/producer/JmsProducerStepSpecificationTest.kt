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

package io.qalipsis.plugins.jms.producer

import assertk.all
import assertk.assertThat
import assertk.assertions.*
import io.aerisconsulting.catadioptre.getProperty
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.steps.DummyStepSpecification
import io.qalipsis.plugins.jms.jms
import org.apache.activemq.ActiveMQConnectionFactory
import org.apache.activemq.command.ActiveMQTopic
import org.junit.jupiter.api.Test

/**
 *
 * @author Alexander Sosnovsky
 * */
internal class JmsProducerStepSpecificationTest {

    private val connectionFactory = ActiveMQConnectionFactory().createConnection()

    @Test
    internal fun `should apply connection`() {
        val rec1 = JmsProducerRecord(
            destination = ActiveMQTopic().createDestination("dest-1"),
            value = "text-1"
        )
        val rec2 = JmsProducerRecord(
            destination = ActiveMQTopic().createDestination("dest-2"),
            value = "text-2"
        )

        val recordSupplier: (suspend (ctx: StepContext<*, *>, input: Any?) -> List<JmsProducerRecord>) = { _, _ -> listOf(rec1, rec2) }

        val previousStep = DummyStepSpecification()
        previousStep.jms().produce {
            name = "my-producer-step"
            connect { connectionFactory }
            records(recordSupplier)
        }

        assertThat(previousStep.nextSteps[0]).isInstanceOf(JmsProducerStepSpecificationImpl::class).all {
            prop("name") { JmsProducerStepSpecificationImpl<*>::name.call(it) }.isEqualTo("my-producer-step")

            prop(JmsProducerStepSpecificationImpl<*>::recordsFactory).isEqualTo(recordSupplier)

            prop(JmsProducerStepSpecificationImpl<*>::metrics).isNotNull().all {
                prop(JmsProducerMetricsConfiguration::bytesCount).isFalse()
                prop(JmsProducerMetricsConfiguration::recordsCount).isFalse()
            }
        }

        val step = previousStep.nextSteps[0] as JmsProducerStepSpecification<*>
        val connectFactory = step.getProperty<() -> String>("connectionFactory")
        assertThat(connectFactory.invoke()).isEqualTo(connectionFactory)
    }


    @Test
    internal fun `should apply bytes count`() {
        val recordSuplier: (suspend (ctx: StepContext<*, *>, input: Any?) -> List<JmsProducerRecord>) = { _, _ -> listOf() }

        val scenario = DummyStepSpecification()
        scenario.jms().produce {
            connect { connectionFactory }
            records(recordSuplier)

            metrics {
                bytesCount = true
            }
        }

        assertThat(scenario.nextSteps[0]).isInstanceOf(JmsProducerStepSpecificationImpl::class).all {
            prop(JmsProducerStepSpecificationImpl<*>::metrics).isNotNull().all {
                prop(JmsProducerMetricsConfiguration::bytesCount).isTrue()
                prop(JmsProducerMetricsConfiguration::recordsCount).isFalse()
            }
        }
    }

    @Test
    internal fun `should apply records count`() {
        val recordSuplier: (suspend (ctx: StepContext<*, *>, input: Any?) -> List<JmsProducerRecord>) = { _, _ -> listOf() }

        val scenario = DummyStepSpecification()
        scenario.jms().produce {
            connect { connectionFactory }
            records(recordSuplier)

            metrics {
                recordsCount = true
            }
        }

        assertThat(scenario.nextSteps[0]).isInstanceOf(JmsProducerStepSpecificationImpl::class).all {
            prop(JmsProducerStepSpecificationImpl<*>::metrics).isNotNull().all {
                prop(JmsProducerMetricsConfiguration::bytesCount).isFalse()
                prop(JmsProducerMetricsConfiguration::recordsCount).isTrue()
            }
        }
    }

}
