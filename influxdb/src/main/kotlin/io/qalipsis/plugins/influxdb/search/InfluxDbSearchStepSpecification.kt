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
     * Defines the statement to execute when searching.
     */
    fun query(queryFactory: suspend (ctx: StepContext<*, *>, input: I) -> String)

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

    var connectionConfig = InfluxDbStepConnectionImpl()

    var queryFactory: suspend (ctx: StepContext<*, *>, input: I) -> String = { _, _ -> "" }

    var monitoringConfig = StepMonitoringConfiguration()

    override fun connect(connectionConfiguration: InfluxDbStepConnectionImpl.() -> Unit) {
        connectionConfig.connectionConfiguration()
    }

    override fun query(queryFactory: suspend (ctx: StepContext<*, *>, input: I) -> String) {
        this.queryFactory = queryFactory
    }

    override fun monitoring(monitoringConfig: StepMonitoringConfiguration.() -> Unit) {
        this.monitoringConfig.monitoringConfig()
    }
}

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
