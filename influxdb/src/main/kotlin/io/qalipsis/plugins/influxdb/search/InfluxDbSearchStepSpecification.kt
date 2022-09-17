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

package io.qalipsis.plugins.influxdb.search

import io.qalipsis.api.annotations.Spec
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.steps.AbstractStepSpecification
import io.qalipsis.api.steps.ConfigurableStepSpecification
import io.qalipsis.api.steps.StepMonitoringConfiguration
import io.qalipsis.api.steps.StepSpecification
import io.qalipsis.plugins.influxdb.InfluxDbStepConnectionImpl
import io.qalipsis.plugins.influxdb.InfluxdbStepSpecification

/**
 * Specification for a [io.qalipsis.plugins.influxdb.search.InfluxDbSearchStep] to search data from a InfluxDB.
 *
 * @author Palina Bril
 */
interface InfluxDbSearchStepSpecification<I> :
    StepSpecification<I, InfluxDbSearchResult<I>, InfluxDbSearchStepSpecification<I>>,
    ConfigurableStepSpecification<I, InfluxDbSearchResult<I>, InfluxDbSearchStepSpecification<I>>,
    InfluxdbStepSpecification<I, InfluxDbSearchResult<I>, InfluxDbSearchStepSpecification<I>> {

    /**
     * Configures the connection to the InfluxDb server.
     */
    fun connect(connectionConfiguration: InfluxDbStepConnectionImpl.() -> Unit)

    /**
     * Defines the statement to execute when searching. The query must contain ordering clauses.
     */
    fun search(searchConfiguration: InfluxDbQueryConfiguration<I>.() -> Unit)

    /**
     * Configures the monitoring of the search step.
     */
    fun monitoring(monitoringConfig: StepMonitoringConfiguration.() -> Unit)
}

/**
 * Implementation of [InfluxDbSearchStepSpecification].
 *
 * @author Palina Bril
 */
@Spec
internal class InfluxDbSearchStepSpecificationImpl<I> :
    InfluxDbSearchStepSpecification<I>,
    AbstractStepSpecification<I, InfluxDbSearchResult<I>, InfluxDbSearchStepSpecification<I>>() {

    internal var connectionConfig = InfluxDbStepConnectionImpl()

    internal var searchConfig = InfluxDbQueryConfiguration<I>()

    internal var monitoringConfig = StepMonitoringConfiguration()

    override fun connect(connectionConfiguration: InfluxDbStepConnectionImpl.() -> Unit) {
        connectionConfig.connectionConfiguration()
    }

    override fun search(searchConfiguration: InfluxDbQueryConfiguration<I>.() -> Unit) {
        searchConfig.searchConfiguration()
    }

    override fun monitoring(monitoringConfig: StepMonitoringConfiguration.() -> Unit) {
        this.monitoringConfig.monitoringConfig()
    }
}

/**
 * @property query closure to generate the query
 */
@Spec
data class InfluxDbQueryConfiguration<I>(
    internal var query: suspend (ctx: StepContext<*, *>, input: I) -> String = { _, _ -> "" },
)

/**
 * Searches data in InfluxDB using a io.qalipsis.plugins.influxdb.search query.
 *
 * @author Palina Bril
 */
fun <I> InfluxdbStepSpecification<*, I, *>.search(
    configurationBlock: InfluxDbSearchStepSpecification<I>.() -> Unit
): InfluxDbSearchStepSpecification<I> {
    val step = InfluxDbSearchStepSpecificationImpl<I>()
    step.configurationBlock()
    this.add(step)
    return step
}
