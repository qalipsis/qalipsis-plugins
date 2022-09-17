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

package io.qalipsis.plugins.influxdb.poll

import com.influxdb.query.FluxRecord
import io.qalipsis.api.annotations.Spec
import io.qalipsis.api.scenario.StepSpecificationRegistry
import io.qalipsis.api.steps.AbstractStepSpecification
import io.qalipsis.api.steps.BroadcastSpecification
import io.qalipsis.api.steps.ConfigurableStepSpecification
import io.qalipsis.api.steps.LoopableSpecification
import io.qalipsis.api.steps.SingletonConfiguration
import io.qalipsis.api.steps.SingletonType
import io.qalipsis.api.steps.StepMonitoringConfiguration
import io.qalipsis.api.steps.StepSpecification
import io.qalipsis.api.steps.UnicastSpecification
import io.qalipsis.plugins.influxdb.InfluxDbStepConnection
import io.qalipsis.plugins.influxdb.InfluxDbStepConnectionImpl
import io.qalipsis.plugins.influxdb.InfluxdbScenarioSpecification
import io.qalipsis.plugins.influxdb.InfluxdbStepSpecification
import java.time.Duration
import javax.validation.constraints.NotNull


@Spec
interface InfluxDbPollStepSpecification :
    StepSpecification<Unit, InfluxDbPollResults, InfluxDbPollStepSpecification>,
    InfluxdbStepSpecification<Unit, InfluxDbPollResults, InfluxDbPollStepSpecification>,
    ConfigurableStepSpecification<Unit, InfluxDbPollResults, InfluxDbPollStepSpecification>,
    LoopableSpecification, UnicastSpecification, BroadcastSpecification {

    /**
     * Configures the connection to the InfluxDB Server.
     */
    fun connect(connection: InfluxDbStepConnection.() -> Unit)

    /**
     * Creates the factory to execute to poll data.
     */
    fun query(query: String)

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

    /**
     * Returns the values individually.
     */
    fun flatten(): StepSpecification<Unit, FluxRecord, *>
}

@Spec
internal class InfluxDbPollStepSpecificationImpl(
) : AbstractStepSpecification<Unit, InfluxDbPollResults, InfluxDbPollStepSpecification>(),
    InfluxDbPollStepSpecification {

    override val singletonConfiguration: SingletonConfiguration = SingletonConfiguration(SingletonType.UNICAST)

    val connectionConfiguration = InfluxDbStepConnectionImpl()

    val monitoringConfiguration = StepMonitoringConfiguration()

    @field:NotNull
    internal lateinit var query: String

    @field:NotNull
    internal var pollPeriod: Duration = Duration.ofSeconds(10L)

    internal var flattenOutput = false

    override fun connect(connection: InfluxDbStepConnection.() -> Unit) {
        this.connectionConfiguration.connection()
    }

    override fun query(query: String) {
        this.query = query
    }

    override fun pollDelay(delay: Duration) {
        pollPeriod = delay
    }

    override fun monitoring(monitoring: StepMonitoringConfiguration.() -> Unit) {
        monitoringConfiguration.monitoring()
    }

    override fun flatten(): StepSpecification<Unit, FluxRecord, *> {
        flattenOutput = true

        @Suppress("UNCHECKED_CAST")
        return this as StepSpecification<Unit, FluxRecord, *>
    }
}

/**
 * Creates an InfluxDB poll step in order to periodically fetch data from an InfluxDB server.
 *
 * This step is generally used in conjunction with a left join to assert data or inject them in a workflow
 *
 * @author Eric Jessé
 */
fun InfluxdbScenarioSpecification.poll(
    configurationBlock: InfluxDbPollStepSpecification.() -> Unit
): InfluxDbPollStepSpecification {
    val step = InfluxDbPollStepSpecificationImpl()
    step.configurationBlock()

    (this as StepSpecificationRegistry).add(step)
    return step
}
