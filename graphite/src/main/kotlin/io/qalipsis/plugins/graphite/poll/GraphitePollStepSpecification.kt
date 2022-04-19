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
import io.qalipsis.plugins.graphite.GraphiteScenarioSpecification
import io.qalipsis.plugins.graphite.GraphiteStepSpecification
import io.qalipsis.plugins.graphite.poll.model.GraphiteQuery
import io.qalipsis.plugins.graphite.render.GraphiteSearchConnectionSpecification
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
    // Implement GraphiteSearchConnectionSpecification into a concrete class.
    fun connect(connection: GraphiteSearchConnectionSpecification.() -> Unit) //this should be affected

    /**
     * Creates query to poll the data.
     */
    fun query(queryBuilder: GraphiteQuery)

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

    val connectionConfiguration = GraphiteSearchConnectionSpecificationImpl()

    val monitoringConfiguration = StepMonitoringConfiguration()

    @field:NotNull
    internal lateinit var queryBuilder: GraphiteQuery

    @field:NotNull
    @field:PositiveDuration
    internal var pollPeriod: Duration = Duration.ofSeconds(10L)

    override fun connect(connection: GraphiteSearchConnectionSpecification.() -> Unit) {
        this.connectionConfiguration .connection()
    }

    override fun query(queryBuilder: GraphiteQuery) {
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


/**
 * Implementation of [GraphiteSearchConnectionSpecification]
 */
class GraphiteSearchConnectionSpecificationImpl: GraphiteSearchConnectionSpecification {

    var url: String = "http://127.0.0.1:8086"

    var username: String = ""

    var password: String = ""
    override fun server(url: String) {
        this.url = url
    }

    override fun basicAuthentication(username: String, password: String) {
        this.username = username
        this.password = password
    }
}
