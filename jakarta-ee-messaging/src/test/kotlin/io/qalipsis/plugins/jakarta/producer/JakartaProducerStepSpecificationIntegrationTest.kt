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

import assertk.all
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.isTrue
import assertk.assertions.prop
import io.aerisconsulting.catadioptre.getProperty
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.steps.DummyStepSpecification
import io.qalipsis.plugins.jakarta.Constants
import io.qalipsis.plugins.jakarta.jakarta
import jakarta.jms.Connection
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory
import org.apache.activemq.artemis.jms.client.ActiveMQDestination
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.testcontainers.containers.GenericContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import kotlin.math.pow

/**
 * @author Krawist Ngoben
 * */
@Testcontainers
internal class JakartaProducerStepSpecificationIntegrationTest {

    private lateinit var connectionFactory: Connection

    @BeforeAll
    fun initGlobal() {
        connectionFactory = ActiveMQConnectionFactory(
            "tcp://localhost:${container.getMappedPort(61616)}",
            Constants.CONTAINER_USER_NAME,
            Constants.CONTAINER_PASSWORD
        ).createConnection()
    }

    @Test
    internal fun `should apply connection`() {
        val rec1 = JakartaProducerRecord(
            destination = ActiveMQDestination.createDestination("dest-1", ActiveMQDestination.TYPE.DESTINATION),
            value = "text-1"
        )
        val rec2 = JakartaProducerRecord(
            destination = ActiveMQDestination.createDestination("dest-2", ActiveMQDestination.TYPE.DESTINATION),
            value = "text-2"
        )

        val recordSupplier: (suspend (ctx: StepContext<*, *>, input: Any?) -> List<JakartaProducerRecord>) =
            { _, _ -> listOf(rec1, rec2) }

        val previousStep = DummyStepSpecification()
        previousStep.jakarta().produce {
            name = "my-producer-step"
            connect { connectionFactory }
            records(recordSupplier)
        }

        assertThat(previousStep.nextSteps[0]).isInstanceOf(JakartaProducerStepSpecificationImpl::class).all {
            prop("name") { JakartaProducerStepSpecificationImpl<*>::name.call(it) }.isEqualTo("my-producer-step")

            prop(JakartaProducerStepSpecificationImpl<*>::recordsFactory).isEqualTo(recordSupplier)

            prop(JakartaProducerStepSpecificationImpl<*>::metrics).isNotNull().all {
                prop(JakartaProducerMetricsConfiguration::bytesCount).isFalse()
                prop(JakartaProducerMetricsConfiguration::recordsCount).isFalse()
            }
        }

        val step = previousStep.nextSteps[0] as JakartaProducerStepSpecification<*>
        val connectFactory = step.getProperty<() -> String>("connectionFactory")
        assertThat(connectFactory.invoke()).isEqualTo(connectionFactory)
    }


    @Test
    internal fun `should apply bytes count`() {
        val recordSuplier: (suspend (ctx: StepContext<*, *>, input: Any?) -> List<JakartaProducerRecord>) =
            { _, _ -> listOf() }

        val scenario = DummyStepSpecification()
        scenario.jakarta().produce {
            connect { connectionFactory }
            records(recordSuplier)

            metrics {
                bytesCount = true
            }
        }

        assertThat(scenario.nextSteps[0]).isInstanceOf(JakartaProducerStepSpecificationImpl::class).all {
            prop(JakartaProducerStepSpecificationImpl<*>::metrics).isNotNull().all {
                prop(JakartaProducerMetricsConfiguration::bytesCount).isTrue()
                prop(JakartaProducerMetricsConfiguration::recordsCount).isFalse()
            }
        }
    }

    @Test
    internal fun `should apply records count`() {
        val recordSuplier: (suspend (ctx: StepContext<*, *>, input: Any?) -> List<JakartaProducerRecord>) =
            { _, _ -> listOf() }

        val scenario = DummyStepSpecification()
        scenario.jakarta().produce {
            connect { connectionFactory }
            records(recordSuplier)

            metrics {
                recordsCount = true
            }
        }

        assertThat(scenario.nextSteps[0]).isInstanceOf(JakartaProducerStepSpecificationImpl::class).all {
            prop(JakartaProducerStepSpecificationImpl<*>::metrics).isNotNull().all {
                prop(JakartaProducerMetricsConfiguration::bytesCount).isFalse()
                prop(JakartaProducerMetricsConfiguration::recordsCount).isTrue()
            }
        }
    }

    companion object {

        @Container
        @JvmStatic
        val container = GenericContainer<Nothing>(Constants.DOCKER_IMAGE).apply {
            withExposedPorts(61616)
            withCreateContainerCmdModifier {
                it.hostConfig!!.withMemory(256 * 1024.0.pow(2).toLong()).withCpuCount(1)
            }
            withEnv(Constants.CONTAINER_USER_NAME_ENV_KEY, Constants.CONTAINER_USER_NAME)
            withEnv(Constants.CONTAINER_PASSWORD_ENV_KEY, Constants.CONTAINER_PASSWORD)
        }
    }

}
