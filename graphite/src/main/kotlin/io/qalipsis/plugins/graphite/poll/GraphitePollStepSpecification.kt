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

package io.qalipsis.plugins.graphite.poll

import io.qalipsis.api.annotations.Spec
import io.qalipsis.api.constraints.PositiveDuration
import io.qalipsis.api.scenario.StepSpecificationRegistry
import io.qalipsis.api.steps.AbstractStepSpecification
import io.qalipsis.api.steps.BroadcastSpecification
import io.qalipsis.api.steps.LoopableSpecification
import io.qalipsis.api.steps.SingletonConfiguration
import io.qalipsis.api.steps.SingletonType
import io.qalipsis.api.steps.StepMonitoringConfiguration
import io.qalipsis.api.steps.StepSpecification
import io.qalipsis.api.steps.UnicastSpecification
import io.qalipsis.plugins.graphite.GraphiteHttpConnectionSpecificationImpl
import io.qalipsis.plugins.graphite.GraphiteScenarioSpecification
import io.qalipsis.plugins.graphite.GraphiteStepSpecification
import io.qalipsis.plugins.graphite.search.GraphiteHttpConnectionSpecification
import io.qalipsis.plugins.graphite.search.GraphiteQuery
import java.time.Duration
import javax.validation.constraints.NotNull

/**
 * Specification for an [io.qalipsis.api.steps.datasource.IterativeDatasourceStep] to poll data from graphite
 *
 * The output is a GraphitePollResult which contains a list of GraphiteRenderApiJsonResponse and GraphiteQueryMeters
 */
@Spec
interface GraphitePollStepSpecification :
    StepSpecification<Unit, GraphitePollResult, GraphitePollStepSpecification>,
    GraphiteStepSpecification<Unit, GraphitePollResult, GraphitePollStepSpecification>,
    LoopableSpecification, UnicastSpecification, BroadcastSpecification {

    /**
     * Configures the connection to the Graphite Server.
     */
    fun connect(connection: GraphiteHttpConnectionSpecification.() -> Unit) //this should be affected

    /**
     * Creates query to poll the data.
     */
    fun query(queryBuilder: GraphiteQuery.() -> Unit)

    /**
     * Delay between two executions of poll.
     *
     * @param delay the delay to wait between the end of a poll and start of next one
     */
    fun pollDelay(delay: Duration)

    /**
     * Configures the monitoring of the poll step.
     */
    fun monitoring(monitoring: StepMonitoringConfiguration.() -> Unit)

}

@Spec
internal class GraphitePollStepSpecificationImpl :
    AbstractStepSpecification<Unit, GraphitePollResult, GraphitePollStepSpecification>(),
    GraphitePollStepSpecification {

    override val singletonConfiguration: SingletonConfiguration = SingletonConfiguration(SingletonType.UNICAST)

    val connectionConfiguration = GraphiteHttpConnectionSpecificationImpl()

    val monitoringConfiguration = StepMonitoringConfiguration()

    @field:NotNull
    internal var queryBuilder: GraphiteQuery.() -> Unit = {}

    @field:NotNull
    @field:PositiveDuration
    internal var pollPeriod: Duration = Duration.ofSeconds(10L)

    override fun connect(connection: GraphiteHttpConnectionSpecification.() -> Unit) {
        this.connectionConfiguration.connection()
    }

    override fun query(queryBuilder: GraphiteQuery.() -> Unit) {
        this.queryBuilder = queryBuilder
    }

    override fun pollDelay(delay: Duration) {
        pollPeriod = delay
    }

    override fun monitoring(monitoring: StepMonitoringConfiguration.() -> Unit) {
        monitoringConfiguration.monitoring()
    }
}

/**
 * Creates a Graphite poll step in order to periodically fetch data from a Graphite server.
 *
 * This step is generally used in conjunction with a left join to assert data or inject them in a workflow.
 *
 * @author Teyyihan Aksu
 */
fun GraphiteScenarioSpecification.poll(
    configurationBlock: GraphitePollStepSpecification.() -> Unit
): GraphitePollStepSpecification {
    val step = GraphitePollStepSpecificationImpl()
    step.configurationBlock()

    (this as StepSpecificationRegistry).add(step)
    return step
}


