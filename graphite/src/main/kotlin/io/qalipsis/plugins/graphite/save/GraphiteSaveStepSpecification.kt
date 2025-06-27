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

package io.qalipsis.plugins.graphite.save

import io.qalipsis.api.annotations.Spec
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.steps.AbstractStepSpecification
import io.qalipsis.api.steps.ConfigurableStepSpecification
import io.qalipsis.api.steps.StepMonitoringConfiguration
import io.qalipsis.api.steps.StepSpecification
import io.qalipsis.plugins.graphite.GraphiteConnectionSpecification
import io.qalipsis.plugins.graphite.GraphiteConnectionSpecificationImpl
import io.qalipsis.plugins.graphite.GraphiteStepSpecification
import io.qalipsis.plugins.graphite.client.GraphiteRecord

/**
 * Specification for a [io.qalipsis.plugins.graphite.save.GraphiteSaveStep] to save data to a Graphite.
 *
 * @author Palina Bril
 */
interface GraphiteSaveStepSpecification<I> :
    StepSpecification<I, GraphiteSaveResult<I>, GraphiteSaveStepSpecification<I>>,
    ConfigurableStepSpecification<I, GraphiteSaveResult<I>, GraphiteSaveStepSpecification<I>>,
    GraphiteStepSpecification<I, GraphiteSaveResult<I>, GraphiteSaveStepSpecification<I>> {

    /**
     * Configures the connection to the Graphite server.
     */
    fun connect(connectionConfiguration: GraphiteConnectionSpecification.() -> Unit)

    /**
     * Defines the statement to execute when saving.
     */
    fun records(recordsFactory: suspend (ctx: StepContext<*, *>, input: I) -> Collection<GraphiteRecord>)

    /**
     * Configures the monitoring of the save step.
     */
    fun monitoring(monitoringConfig: StepMonitoringConfiguration.() -> Unit)

}

/**
 * Implementation of [GraphiteSaveStepSpecification].
 *
 */
@Spec
internal class GraphiteSaveStepSpecificationImpl<I> :
    GraphiteSaveStepSpecification<I>,
    AbstractStepSpecification<I, GraphiteSaveResult<I>, GraphiteSaveStepSpecification<I>>() {

    internal var connectionConfig = GraphiteConnectionSpecificationImpl()

    internal var records: (suspend (ctx: StepContext<*, *>, input: I) -> Collection<GraphiteRecord>) =
        { _, _ -> emptyList() }

    internal var monitoringConfig = StepMonitoringConfiguration()

    override fun connect(connectionConfiguration: GraphiteConnectionSpecification.() -> Unit) {
        connectionConfig.connectionConfiguration()
    }

    override fun records(recordsFactory: suspend (ctx: StepContext<*, *>, input: I) -> Collection<GraphiteRecord>) {
        this.records = recordsFactory
    }

    override fun monitoring(monitoringConfig: StepMonitoringConfiguration.() -> Unit) {
        this.monitoringConfig.monitoringConfig()
    }
}

/**
 * Saves messages into Graphite.
 *
 */
fun <I> GraphiteStepSpecification<*, I, *>.save(
    configurationBlock: GraphiteSaveStepSpecification<I>.() -> Unit
): GraphiteSaveStepSpecification<I> {
    val step = GraphiteSaveStepSpecificationImpl<I>()
    step.configurationBlock()

    this.add(step)
    return step
}