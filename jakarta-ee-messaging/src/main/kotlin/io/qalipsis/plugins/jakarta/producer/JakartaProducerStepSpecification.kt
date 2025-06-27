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

package io.qalipsis.plugins.jakarta.producer

import io.qalipsis.api.annotations.Spec
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.steps.AbstractStepSpecification
import io.qalipsis.api.steps.ConfigurableStepSpecification
import io.qalipsis.api.steps.StepMonitoringConfiguration
import io.qalipsis.api.steps.StepSpecification
import io.qalipsis.plugins.jakarta.JakartaStepSpecification
import jakarta.jms.Connection
import jakarta.jms.Session
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
     * Configures the session to the Jakarta server.
     */
    fun session(sessionFactory: (connection: Connection) -> Session)

    /**
     * Defines the count of producing threads to create, defaults to 1.
     */
    fun producers(count: Int)

    /**
     * records closure to generate a list of [JakartaProducerRecord]
     */
    fun records(recordsFactory: suspend (ctx: StepContext<*, *>, input: I) -> List<JakartaProducerRecord>)

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

    internal var sessionFactory: (connection: Connection) -> Session = { connection -> connection.createSession() }

    internal var producersCount = 1

    internal var recordsFactory: suspend (ctx: StepContext<*, *>, input: I) -> List<JakartaProducerRecord> =
        { _, _ -> listOf() }

    internal var monitoringConfig = StepMonitoringConfiguration()

    override fun connect(connectionFactory: () -> Connection) {
        this.connectionFactory = connectionFactory
    }

    override fun producers(count: Int) {
        producersCount = count
    }

    override fun session(sessionFactory: (connection: Connection) -> Session) {
        this.sessionFactory = sessionFactory
    }

    override fun records(recordsFactory: suspend (ctx: StepContext<*, *>, input: I) -> List<JakartaProducerRecord>) {
        this.recordsFactory = recordsFactory
    }

    override fun monitoring(monitoringConfig: StepMonitoringConfiguration.() -> Unit) {
        this.monitoringConfig.monitoringConfig()
    }

}


/**
 * Provides [jakarta.jms.Message] to JMS server using an io.qalipsis.plugins.jakarta.producer query.
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
